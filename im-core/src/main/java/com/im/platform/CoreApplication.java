package com.im.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * im-core:IM 平台的核心域,合并了原来独立部署的 session(连接状态/鉴权)、
 * biz(用户/群组 DDD)、msg(消息读写)、sync(多端增量同步)、status(在线状态)、
 * idgen(发号,现在是纯内部组件)、dfs(文件上传凭证)。
 *
 * 原来 bff"协议语义 → 内部 RPC 翻译"的职责,现在由 im-gateway 的 MethodRegistry 直接承担——
 * gateway 解密出 method_id 之后直接按类型化的 gRPC stub 调用 im-core,不需要再多一跳专门做翻译的服务,
 * im-core 内部原来的 bff 占位包已经删掉。
 *
 * 内容处理管道(原 moderation/translation/ai-analysis)不再是常驻服务,改成
 * {@link com.im.platform.core.callback.CallbackInvoker} 驱动的回调钩子,由业务系统自己接管。
 *
 * 对外只暴露一个 gRPC 端口(见 {@link com.im.platform.core.grpc.GrpcServerLifecycle})和一个
 * Actuator HTTP 端口,不再需要 Nacos 做服务发现——因为服务之间的调用现在都是同一个 JVM 里的
 * 方法调用。im-gateway 仍然独立部署(连接层的扩缩容特性和这里完全不同)。
 *
 * 这个类特意放在 com.im.platform 根包下(而不是 com.im.platform.core):MyBatis-Plus 的零配置
 * mapper 扫描(没用 @MapperScan,靠每个 Mapper 接口自己的 @Mapper 注解)是基于
 * "主启动类所在包"递归扫描的,和 @SpringBootApplication(scanBasePackages=...) 是两回事——
 * 放在子包下会导致 biz/msg/sync/dfs 里的 Mapper 一个都扫不到。
 */
@SpringBootApplication
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
