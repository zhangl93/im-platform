-- im-core 初始 schema。之前一直是联调时手工 CREATE TABLE,现在收进 Flyway 管理,
-- 每次改表结构都新增一个 V<N>__xxx.sql,不直接改这个文件(Flyway 靠校验和防止已应用的迁移被改动)。
--
-- 参考 OpenIM 的表设计惯例:
--   1. 有业务含义、用户后续可能想挂自定义属性的表,统一加一个 `ex` 字段(VARCHAR(1024),
--      内容格式由业务自己定义,通常是 JSON 字符串,平台不解析、不校验)。纯关系表
--      (t_user_block)和内部同步机制的表(t_update_log)不加——没有"业务属性"可扩展。
--   2. 不加表间外键约束。各表分属不同业务域(biz/msg/sync/dfs),且 t_message/t_update_log
--      是未来要按 chat_id/user_id 分片的表,跨分片 FK 本来就不可行,统一不用,完整性靠应用层保证。
--   3. 字符集用 utf8mb4(不是 utf8):要装下 emoji 和部分生僻字,3 字节的 utf8 装不下。

-- ============================================================
-- biz:用户
-- ============================================================
CREATE TABLE t_user (
    user_id    BIGINT       NOT NULL COMMENT '用户ID,idgen 生成,应用层赋值',
    nickname   VARCHAR(64)  NULL     COMMENT '昵称',
    avatar     VARCHAR(255) NULL     COMMENT '头像URL',
    status     INT          NOT NULL DEFAULT 0 COMMENT '0=NORMAL 1=BLOCKED 2=DEACTIVATED,对应 UserStatus 枚举 ordinal',
    ex         VARCHAR(1024) NULL    COMMENT '扩展字段,业务自定义属性,平台不解析',
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

-- ============================================================
-- biz:拉黑关系。纯关系表,没有独立的业务属性可扩展,不加 ex。
-- ============================================================
CREATE TABLE t_user_block (
    id              BIGINT AUTO_INCREMENT COMMENT '自增主键,MyBatis-Plus 实体里不映射,纯 DB 层需要',
    user_id         BIGINT NOT NULL COMMENT '发起拉黑的用户',
    blocked_user_id BIGINT NOT NULL COMMENT '被拉黑的用户',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_blocked (user_id, blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户拉黑关系';

-- ============================================================
-- biz:群组
-- ============================================================
CREATE TABLE t_group (
    group_id         BIGINT       NOT NULL COMMENT '群ID,idgen 生成,应用层赋值',
    group_name       VARCHAR(128) NULL     COMMENT '群名称',
    max_member_count INT          NULL     COMMENT '成员上限,对应 GroupPolicy',
    ex               VARCHAR(1024) NULL    COMMENT '扩展字段,业务自定义属性,平台不解析',
    PRIMARY KEY (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组';

-- ============================================================
-- biz:群成员
-- ============================================================
CREATE TABLE t_group_member (
    id        BIGINT AUTO_INCREMENT COMMENT '自增主键,MyBatis-Plus 实体里不映射,纯 DB 层需要',
    group_id  BIGINT      NOT NULL COMMENT '群ID',
    user_id   BIGINT      NOT NULL COMMENT '用户ID',
    role      VARCHAR(16) NOT NULL COMMENT 'OWNER / ADMIN / MEMBER,对应 GroupRole 枚举 name()',
    joined_at BIGINT      NOT NULL COMMENT '入群时间,毫秒时间戳',
    ex        VARCHAR(1024) NULL  COMMENT '扩展字段(成员备注名、自定义头衔等),业务自定义,平台不解析',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_user (group_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群成员';

-- ============================================================
-- msg:消息。分片键是 chat_id(同会话数据落同分片),ShardingSphere 规则接入之前先建成单表。
-- ============================================================
CREATE TABLE t_message (
    message_id    BIGINT       NOT NULL COMMENT '消息ID,idgen 生成,低位编码 chat_id 分片信息',
    chat_id       BIGINT       NOT NULL COMMENT '会话ID(单聊/群聊),未来的分片键',
    sender_id     BIGINT       NOT NULL COMMENT '发送者用户ID',
    content       MEDIUMBLOB   NULL     COMMENT '消息内容,二进制,具体编码由 msg_type 决定',
    msg_type      INT          NOT NULL COMMENT '消息类型(文本/图片/自定义...)',
    server_time   BIGINT       NOT NULL COMMENT '服务端落库时间,毫秒时间戳',
    client_msg_id VARCHAR(64)  NOT NULL COMMENT '客户端生成的幂等键',
    ex            VARCHAR(1024) NULL    COMMENT '扩展字段(消息级自定义数据,比如表情回应),业务自定义,平台不解析',
    PRIMARY KEY (message_id),
    KEY idx_chat_message (chat_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息';

-- ============================================================
-- sync:多端增量同步日志。内部同步机制,不是面向业务的实体,不加 ex。
-- 分片键是 user_id,ShardingSphere 规则接入之前先建成单表。
-- ============================================================
CREATE TABLE t_update_log (
    id         BIGINT AUTO_INCREMENT COMMENT '自增主键,MyBatis-Plus 实体里不映射,纯 DB 层需要',
    user_id    BIGINT     NOT NULL COMMENT '用户ID,未来的分片键',
    seq        BIGINT     NOT NULL COMMENT '该用户维度单调递增的同步位点,SeqAllocator 生成',
    event_type INT        NOT NULL COMMENT '更新事件类型(新消息/群变更/资料变更...)',
    payload    MEDIUMBLOB NULL     COMMENT '事件负载,二进制',
    created_at BIGINT     NOT NULL COMMENT '写入时间,毫秒时间戳',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_seq (user_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多端增量同步日志';

-- ============================================================
-- dfs:文件元数据
-- ============================================================
CREATE TABLE t_file (
    file_id      BIGINT       NOT NULL COMMENT '文件ID,idgen 生成,应用层赋值',
    owner_id     BIGINT       NOT NULL COMMENT '上传者用户ID',
    file_name    VARCHAR(255) NULL     COMMENT '原始文件名',
    object_key   VARCHAR(500) NULL     COMMENT 'MinIO 对象存储的 key',
    file_size    BIGINT       NULL     COMMENT '文件大小(字节)',
    content_type VARCHAR(100) NULL     COMMENT 'MIME 类型',
    status       INT          NOT NULL DEFAULT 0 COMMENT '0=待上传 1=已完成',
    ex           VARCHAR(1024) NULL    COMMENT '扩展字段,业务自定义属性,平台不解析',
    PRIMARY KEY (file_id),
    KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据';
