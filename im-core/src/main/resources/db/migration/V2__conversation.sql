-- 单聊会话映射:chat_id <-> (user_a, user_b)。群聊不需要这张表,群聊直接用 group_id 当 chat_id,
-- 参与者查 t_group_member。user_a 恒小于 user_b(应用层保证,见 ConversationService),
-- 这样 (user_a, user_b) 的唯一索引就能同时防止 (1,2) 和 (2,1) 被建成两条不同会话。
CREATE TABLE t_conversation (
    chat_id    BIGINT NOT NULL COMMENT '会话ID,idgen 生成,应用层赋值',
    user_a     BIGINT NOT NULL COMMENT '较小的 user_id',
    user_b     BIGINT NOT NULL COMMENT '较大的 user_id',
    created_at BIGINT NOT NULL COMMENT '创建时间,毫秒时间戳',
    PRIMARY KEY (chat_id),
    UNIQUE KEY uk_user_pair (user_a, user_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单聊会话映射';
