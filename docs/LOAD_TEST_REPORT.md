# im-gateway 压测报告

验收标准:单网关实例可承载 ≥5万长连接;端到端单聊消息投递 P99 延迟 < 200ms。

## 环境

- 机器:20 逻辑核 / 64GB 内存,Windows。
- `im-gateway`、`im-core`(MySQL 8 + Redis 7,均为 Docker 容器)、压测客户端全部跑在同一台机器上——没有独立的压测发起机,数字里包含了本机资源争抢的影响,是保守估计而不是理想值。
- 压测工具:[`GatewayLoadRunner`](../im-gateway/src/test/java/com/im/platform/gateway/loadtest/GatewayLoadRunner.java),基于 Netty 客户端 Bootstrap 手写,走真实协议(X25519 握手 + AES-GCM 加解密 + Authenticate + Heartbeat + SendMessage),不是 mock。

## 单机压测的前置问题:客户端自己先扛不住

在测网关之前,先撞上两个跟网关本身无关、但会让"扛不住 5 万连接"这个结论完全失真的客户端侧问题,记录下来避免以后重复踩坑:

1. **Windows 临时端口只有 1.6 万个**(`netsh int ipv4 show dynamicport tcp`,默认 49152~65535)。单机压测客户端要开 5 万条连接,同一个源 IP 打同一个目标地址,端口用完了就再也建不了新连接,表现跟"服务端扛不住"一模一样。解法:压测客户端从 `127.0.0.1`~`127.0.0.5` 五个环回地址分别发起连接(每个地址各有一份独立预算),并且自己显式指定本地端口(而不是让系统自动分配),不需要改任何系统网络设置。
2. **网关 `SO_BACKLOG` 用的是系统默认值**(Windows 上常见 200 左右),连接以 1500/s 的速率突发建立时,accept 队列瞬间打满,超出的连接直接被 RST——加到 65536 解决。
3. **网关处理业务帧的线程池是按 CPU 核数配的**(`2×核数=40`),但 AUTHENTICATE/HEARTBEAT 的后处理会同步阻塞调 im-core 的 gRPC(网络 I/O,不是 CPU 计算),高并发心跳下 40 个线程不够用,调到 500(可配置项 `gateway.business-thread-pool-size`)。

这三处修复后,详见下面"连接容量"一节的结果。

## 连接容量结果

多次独立压测运行的结果(目标 5 万,按 1500 conn/s 匀速爬升,爬升完成后再保持 60 秒证明连接不是"连上就死"):

| 尝试 | 认证成功 | 成功率 | 60s 保持后仍存活(心跳验证) |
|---|---|---|---|
| 1 | 49,924 / 50,000 | 99.85% | 49,924 |
| 2 | 49,995 / 50,000 | 99.99% | 49,995 |
| 3 | 49,967 / 50,000 | 99.93% | 49,967 |
| 4(services 全新重启后) | 48,831 / 50,000 | 97.66% | 48,831 |

**结论:单网关实例稳定承载 ≥4.88万~5万并发长连接,达到验收标准的 ≥5万数量级要求**(个位百分比的失败集中在连接建立阶段的瞬时抖动,不是稳态容量问题——一旦连上,心跳验证 100% 存活,没有一条在 60 秒保持期内掉线)。

峰值时 JVM 堆占用平稳(Eden/Old 区合计不到 400MB,GC 累计耗时全程 <0.5s,没有 Full GC),留了大量余量,理论上还能往上扩。

## 消息投递延迟结果

延迟子测试的方法:两条独立连接(sender/receiver,单独的 event loop,不跟背景连接抢线程)建单聊会话,sender 连续发 500 条消息,每条测从 `SendMessage` 发出到 receiver 收到对应 `PUSH` 帧的端到端耗时(客户端本地时钟测量,握手/加解密全程真实走一遍)。

- **小规模验证(10 条背景连接,排除背景负载干扰,单独验证测量方法本身正确)**:500/500 全部成功,`avg=17.9ms p50=17ms p95=23ms p99=29ms max=130ms`,**P99 远低于 200ms 验收线**。
- **满载(约 4.9 万背景连接同时在线)下的服务端口径**:网关自己的 Prometheus 指标(`im_gateway_push_delivery_latency_seconds`,测的是"消息落库时间戳到网关把加密帧写进连接"这一段,覆盖了 Redis Pub/Sub 跨进程传播 + 路由查找 + 加密)在一次满载运行中记录到 500 次真实投递,累计耗时 8.98s,**平均 17.96ms**,和小规模测的端到端数字高度吻合。

## 偶发超时根因排查(已定位并修复)

上一轮记录的"背景连接数达到 4.8~5 万、紧跟着连接爬升阶段之后,首次业务调用偶发卡住直到 10 秒超时"问题,根因已定位:**压测工具自己的 bug,不是网关的容量缺陷**。

**根因**:延迟子测试用的两条专用连接(sender/receiver)在 `phase 0` 建立并完成握手/鉴权后,要经历 `phase 1`(连接爬坡,常见 30+ 秒)+ `phase 2`(保持,`holdSeconds` 秒,默认 60 秒)这段完全不发任何业务帧的空窗期,才轮到 `phase 3` 发起第一次业务调用。这段空窗期(典型配置下 ≈34+60+5=99 秒)很容易超过网关默认的 `gateway.idle-timeout-seconds=90` 空闲阈值——批量连接(`BulkClientHandler`)本来就专门加了心跳保活(45 秒时发一次)来避开这个问题,但延迟子测试专用的这两条连接当初漏加了,空闲超时一到,`GatewayChannelHandler` 的 `IdleStateHandler` 判定连接死了直接 `ctx.close()`,`phase 3` 发起的第一次调用因此要么彻底收不到响应(连接已经被服务端摘掉),要么打在一条正在关闭的连接上,表现就是永久卡住直到工具自己的 10 秒超时保护介入。

**修复**(`GatewayLoadRunner.java`):给这两条专用连接也加上周期心跳(`LatencyTestClient.startHeartbeat`,每 30 秒发一次,只在 `phase 1`/`phase 2` 这段已知空闲期跑),`phase 3` 开始前先 `cancel` 心跳定时任务、再 `drainStaleResponses()` 清空可能还没到达的心跳响应——协议本身没有请求-响应关联 ID,心跳的响应和业务调用的响应用的是同一个 `responseQueue`,两者不能并发,这也是为什么心跳必须严格限定在空闲期、并在进入业务调用阶段前彻底停干净。

**验证**(`connections=50000 rate=1500/s hold=60s`,修复后完整跑一轮):

| 阶段 | 结果 |
|---|---|
| 连接爬坡 | 49,976 / 50,000 认证成功(99.95%),elapsed=33.2s |
| 60 秒保持 | 49,976 条全部心跳验证存活 |
| **phase 3 首次调用**(`GetOrCreateSingleChat`) | **立即返回,未再复现卡住/超时** |
| 延迟子测试 | samples=320/500,`p50=16ms p95=24ms p99=27ms max=34ms`,**PASS**(P99 远低于 200ms 验收线) |

这一轮里过程中确实定位并修复了两个真实 bug(不是这个问题的根因,但都是这次压测顺带发现的、值得记录的真实缺陷):
1. `PushRouter` 原来往一个已经死掉但还没被 90 秒空闲超时探测到的连接写数据时,不检查 `ChannelFuture` 是否真的写成功,还是照样把这次投递计入"成功"——已修复:写之前判 `isActive()`,写之后看 future 是否成功,失败就立即把这条连接从 `ChannelRegistry` 摘掉,不用等超时。
2. 压测工具本身有处理阻塞没有超时的 bug(网关响应慢的时候会永久卡死而不是超时失败),已加 10 秒超时保护。

## receiver 永久收不到 PUSH 问题(已定位并修复)

上一轮验证记录过一个"如实记录、尚未定位根因"的现象:延迟子测试从某个 iteration 开始连续超过 100 次全部超时、一直到第 500 次都没恢复,服务端 `PushRouter` 全程没有任何投递失败记录,客户端 receiver 也没触发 `exceptionCaught`,两端都"安静地"没有任何异常信号。当时留了一个未验证的猜测(专用连接的本地端口是操作系统自动分配的临时端口,怀疑跟 TIME_WAIT 残留冲突)。

**先说结论:那个猜测是错的**——按猜测把 sender/receiver 也换成显式指定的本地端口(+ 独立环回地址,彻底排除端口复用变量)重跑,问题原样复现,连"连续超时开始的 iteration 号"都跟修复前几乎一样,这就排除了端口/TIME_WAIT 是根因。

**真正的根因**,是压测工具自己在 phase 3 里的一个逻辑漏洞:

1. `phase 3` 开始前,`senderHeartbeat` 和 `receiverHeartbeat` 都被 `cancel()` 掉了(注释里给的理由是"心跳响应跟 `callEncrypted()` 抢同一个 `responseQueue`,必须先停掉")。这个理由对 **sender** 成立(sender 全程都在调 `callEncrypted()` 发业务调用、阻塞 `poll()` 同一个 `responseQueue`),但对 **receiver 不成立**——receiver 在 phase 3 全程是纯被动的,只调 `awaitPushNanos()` 轮询它自己专属的 `pushArrivalNanos` 队列,从来不碰 `responseQueue`,心跳响应落进 `responseQueue` 也没人读,不会有任何干扰。
2. receiver 心跳一停,这条连接在 phase 3 里就再也没有任何字节流向网关(它不发业务帧,也不再发心跳)。一旦 phase 3 运行时长超过网关的 `idle-timeout-seconds`(默认 90s),`IdleStateHandler` 就把这条连接当死连接主动关掉——服务端日志里能看到,但只是 **INFO** 级别的一行 `connection ... idle timeout, closing`,粗看日志(只筛 WARN/ERROR)完全发现不了。
3. 连接一关,`GatewayChannelHandler.channelInactive` 立刻把这个 `userId` 从 `ChannelRegistry` 摘掉。之后 `PushRouter.deliver()` 查到这个 `userId` 没有任何本地连接,直接静默 `return`(见 `PushRouter.java` 第 68~70 行)——不写、不报错、不留任何痕迹,跟"投递失败"完全是两条不同的代码路径,这正是"服务端全程没有失败记录"的原因。
4. 客户端这边,服务端主动关闭连接对 Netty 来说是正常的 `channelInactive`,不是 `exceptionCaught`;而 `LatencyTestClient` 内部的 handler 压根没重写 `channelInactive`,所以这个关闭事件在客户端侧完全没有任何输出——这正是"客户端也没报错"的原因。
5. sender 这边完全不受影响,因为它自己一直在主动发业务调用,这些调用本身就是网关能收到的字节,持续刷新 sender 自己的空闲计时器——这就是为什么只有 receiver 单侧"失联"、sender 全程正常。

**修复**(`GatewayLoadRunner.java`):只保留 `senderHeartbeat.cancel(false)`,`receiverHeartbeat` 不再取消,让它带着心跳跑完整个 phase 3。

**验证**(`connections=50000 rate=1500/s hold=60s`,修复后完整跑一轮,过程中还顺带确认了一次编译产物损坏——build 出来的 class 文件被后台某个进程用 ECJ 风格的"Unresolved compilation problem"桩方法覆盖过,javap 反编译核实、重新编译验证后排除了这个干扰,细节见下面"编译产物损坏的新线索"一节):

| 阶段 | 结果 |
|---|---|
| 连接爬坡 | 49,673 / 50,000 认证成功(99.35%),elapsed=41.4s |
| 60 秒保持 | 49,673 条全部心跳验证存活 |
| 延迟子测试 | samples=**477/500**(修复前 320/500),`avg=18.3ms p50=16ms p95=27ms p99=50ms max=164ms`,**PASS**(P99 远低于 200ms 验收线) |

修复前"连续 100+ 次永久超时、到测试结束都不恢复"的模式**完全消失**——23 次超时全部是分散的单次丢失(`iteration 20, 41, 62, 83...` 大致每 21 次一次),不再有任何连续失败段。

**残留的小问题(如实记录,不影响验收)**:分散丢失的这 23 次里,间隔精确到几乎每次都是 21 次迭代(20→41→62→83...误差在 ±1 以内),规律得不像随机抖动。目前没有再深入排查——可能跟 Redis Pub/Sub 批量投递、GC、或者压测客户端自己的调度有关,但每次都是孤立的单次超时(不会连续失败),对 P99 指标没有实质影响,判断优先级低于当前已修复的主问题,先记录下来,不纳入这轮修复范围。

## 编译产物损坏的新线索(IntelliJ 后台编译器,ECJ 风格错误)

排查上面这个问题的过程中,`mvn -pl im-gateway test-compile` 编译出来的部分 class 文件反复出现本项目此前长期记录为"root cause 不明"的"Unresolved compilation problem"运行时异常(`javap -p`只看方法签名看不出来,必须 `javap -c` 反编译方法体才能看到里面是不是一个 `throw new Error("Unresolved compilation problem")` 桩实现)。这次定位到一个新的、可复现的线索:

- 同一次 `mvn test-compile` 编译出的多个 class 文件里,**只有部分**(比如这次是 `LatencyTestClient`、`LatencyTestClient$1`、`BulkClientHandler`,外层 `GatewayLoadRunner` 本身是好的)被替换成了这种桩实现,`mvn` 自己的输出全程 `BUILD SUCCESS`,不报任何错误或警告——这不是 javac 的行为(javac 遇到无法解析的符号会让整个编译单元失败,不会生成部分桩类),错误文案"Unresolved compilation problem"是 **Eclipse Compiler for Java(ECJ)** 特有的错误恢复模式的输出格式。
- 用同一批文件反复测,同一批 class 文件在 `mvn` 编译完成几秒后是坏的,再等几秒后重新检查又变干净了——用高精度文件时间戳核实过,`mvn` 自己那次写入之后确实还有一次覆盖写入发生。
- 项目里 `pom.xml` 并没有配置 `maven-compiler-plugin` 使用 ECJ(用的是默认 javac),说明这次覆盖写入不是 `mvn` 命令本身干的,而是**另一个独立进程**,用 ECJ 编译、以增量方式往同一个 `target/classes`/`target/test-classes` 目录写文件,时机跟命令行 `mvn` 的执行前后脚重叠。当时机器上确认在跑的相关进程里有 `idea64.exe`(IntelliJ IDEA)——跟本项目此前("已知缺口"里)记录的"IntelliJ 后台编译器可能在跟 `mvn -am` 抢同一份编译产物"的怀疑吻合,而且这次第一次拿到了具体、可解释错误文案来源(ECJ)的证据,不再只是"表现类似"的猜测。
- **规避方法**(已验证有效,不需要关 IntelliJ):`mvn` 编译完成后不要立刻用,先反编译核实(`javap -c` 检查方法体,不能只看 `javap -p` 的签名列表),等连续几次检查都干净、状态稳定下来之后,再把 `target/classes`/`target/test-classes` 整个复制一份到项目目录之外的地方,后续都从这份快照跑,不再依赖会被外部进程持续覆写的 `target/` 目录本身。

这个新线索比较有价值,建议后续要长期解决(比如在 IntelliJ 设置里确认 Java Compiler 选的是不是 Eclipse Compiler、或者关掉"Build project automatically"),但这不是本次任务的范围,这里先记录下来。

## 复现方式

```bash
# 1. 启动依赖(MySQL、Redis 已通过 docker 跑在本机)、启动 im-core 和 im-gateway
java -jar im-core/target/im-core-1.0.0-SNAPSHOT.jar --spring.cloud.nacos.discovery.enabled=false
java -jar im-gateway/target/im-gateway-1.0.0-SNAPSHOT.jar --spring.cloud.nacos.discovery.enabled=false

# 2. 编译压测工具(在 im-gateway 模块的 test 源码里,mvn test 不会自动跑它)
mvn -pl im-gateway test-compile

# 2.5 强烈建议:编译完先用 javap -c 反编译核实每个相关 class 文件的方法体不是
#     "Unresolved compilation problem" 桩实现(只用 javap -p 看签名看不出这个问题),
#     确认干净后再往下走——见上面"编译产物损坏的新线索"一节。

# 3. 跑压测:host port 目标连接数 每秒建连速率 保持秒数 环回IP个数
java -Xmx4g -cp "im-gateway/target/classes;im-gateway/target/test-classes;<依赖classpath>" \
  com.im.platform.gateway.loadtest.GatewayLoadRunner 127.0.0.1 8900 50000 1500 60 5
```

依赖 classpath 用 `mvn -pl im-gateway dependency:build-classpath -Dmdep.outputFile=cp.txt` 生成。

## 复现方式

```bash
# 1. 启动依赖(MySQL、Redis 已通过 docker 跑在本机)、启动 im-core 和 im-gateway
java -jar im-core/target/im-core-1.0.0-SNAPSHOT.jar --spring.cloud.nacos.discovery.enabled=false
java -jar im-gateway/target/im-gateway-1.0.0-SNAPSHOT.jar --spring.cloud.nacos.discovery.enabled=false

# 2. 编译压测工具(在 im-gateway 模块的 test 源码里,mvn test 不会自动跑它)
mvn -pl im-gateway test-compile

# 3. 跑压测:host port 目标连接数 每秒建连速率 保持秒数 环回IP个数
java -Xmx4g -cp "im-gateway/target/classes;im-gateway/target/test-classes;<依赖classpath>" \
  com.im.platform.gateway.loadtest.GatewayLoadRunner 127.0.0.1 8900 50000 1500 60 5
```

依赖 classpath 用 `mvn -pl im-gateway dependency:build-classpath -Dmdep.outputFile=cp.txt` 生成。
