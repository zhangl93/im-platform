-- t_message 从单表切成 4 张物理分片表,分片键 chat_id,路由规则见
-- com.im.platform.msg.sharding.MessageShardingRouter(表名后缀 = chat_id 对 4 取模,MyBatis-Plus
-- DynamicTableNameInnerInterceptor 在运行时改写表名,没有引入 ShardingSphere——它 5.4.1
-- 编译期绑死的 SnakeYAML 1.x API 跟 Spring Boot 3.2.5 管理的 2.x 冲突,见 MessageShardingRouter 类注释)。
-- 注:本文件(含注释)不能出现 dollar-brace 包起来的写法——Flyway 默认会把 SQL 脚本里这种
-- 写法当占位符解析,没配置对应的 flyway.placeholders.* 直接报 "No value provided for
-- placeholder" 导致启动失败,踩过一次坑,写脚本/注释时留意。
-- 这是一个全新起步的项目,t_message 里还没有需要迁移的历史数据,直接删表重建;
-- 真的有存量数据要上分片,这里得换成"建新表 + 按 chat_id % 4 分批回写 + 双写校验 + 切流量"这一套,
-- 不能简单 DROP。

DROP TABLE IF EXISTS t_message;

CREATE TABLE t_message_0 (
    message_id    BIGINT       NOT NULL COMMENT '消息ID,idgen 生成,低位编码 chat_id 分片信息',
    chat_id       BIGINT       NOT NULL COMMENT '会话ID(单聊/群聊),分片键',
    sender_id     BIGINT       NOT NULL COMMENT '发送者用户ID',
    content       MEDIUMBLOB   NULL     COMMENT '消息内容,二进制,具体编码由 msg_type 决定',
    msg_type      INT          NOT NULL COMMENT '消息类型(文本/图片/自定义...)',
    server_time   BIGINT       NOT NULL COMMENT '服务端落库时间,毫秒时间戳',
    client_msg_id VARCHAR(64)  NOT NULL COMMENT '客户端生成的幂等键',
    ex            VARCHAR(1024) NULL    COMMENT '扩展字段(消息级自定义数据,比如表情回应),业务自定义,平台不解析',
    PRIMARY KEY (message_id),
    KEY idx_chat_message (chat_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息,分片0(chat_id % 4 = 0)';

CREATE TABLE t_message_1 LIKE t_message_0;
CREATE TABLE t_message_2 LIKE t_message_0;
CREATE TABLE t_message_3 LIKE t_message_0;

ALTER TABLE t_message_1 COMMENT='消息,分片1(chat_id % 4 = 1)';
ALTER TABLE t_message_2 COMMENT='消息,分片2(chat_id % 4 = 2)';
ALTER TABLE t_message_3 COMMENT='消息,分片3(chat_id % 4 = 3)';
