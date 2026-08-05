-- 每个用户对每个会话(单聊+群聊都覆盖,chat_id 语义与 t_conversation/t_read_cursor 一致)
-- 自己的免打扰/置顶状态。纯个人偏好,不联表 t_conversation——群聊没有 t_conversation 行,
-- 单聊也不需要靠这张表存在与否判断会话是否存在,行不存在就等价于"未设置"(默认不免打扰、不置顶)。
-- 量级跟 t_read_cursor 一样是"用户数 x 会话数"级别,不分片。

CREATE TABLE t_conversation_setting (
    user_id    BIGINT NOT NULL COMMENT '用户ID',
    chat_id    BIGINT NOT NULL COMMENT '会话ID(单聊或群聊)',
    is_muted   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否免打扰',
    is_pinned  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶',
    updated_at BIGINT NOT NULL COMMENT '更新时间,毫秒时间戳',
    PRIMARY KEY (user_id, chat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话级用户偏好设置';
