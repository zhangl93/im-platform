-- 每个用户在每个会话里的已读游标(read_to_message_id = 该用户已读到的最大 message_id)。
-- 更新走 upsert(见 ReadCursorMapper.upsert),取 GREATEST 保证单调递增——网络乱序时
-- 一条"读到更早消息"的请求后到达,不能让游标倒退。
-- 这张表不跟着 t_message 分片:一个用户在一个会话里只有一行,量级是"用户数 x 会话数"
-- 级别,不是"消息数"级别,单表足够。

CREATE TABLE t_read_cursor (
    chat_id            BIGINT NOT NULL COMMENT '会话ID',
    user_id            BIGINT NOT NULL COMMENT '用户ID',
    read_to_message_id BIGINT NOT NULL COMMENT '已读到的消息ID',
    updated_at         BIGINT NOT NULL COMMENT '更新时间,毫秒时间戳',
    PRIMARY KEY (chat_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户已读游标';
