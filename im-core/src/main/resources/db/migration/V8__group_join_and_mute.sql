-- 群组入群模式(OPEN 直接加入 / APPROVAL 需要管理员审核,枚举顺序对应
-- com.im.platform.biz.domain.group.GroupJoinMode)和全员禁言开关,都是可变的群状态,
-- 不是固定配置,所以直接加在 t_group 上,不是 GroupPolicy 那种不可变策略值对象。
ALTER TABLE t_group
    ADD COLUMN join_mode   INT          NOT NULL DEFAULT 0 COMMENT '入群模式:0=OPEN直接加入,1=APPROVAL需审核',
    ADD COLUMN group_muted TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否全员禁言(OWNER/ADMIN 不受影响)';

-- 成员级禁言:0 表示未禁言,非 0 表示禁言到该时间点(毫秒时间戳),到期后自动失效,
-- 不需要定时任务清理——每次发消息时用 mutedUntil > now 现算。
ALTER TABLE t_group_member
    ADD COLUMN muted_until BIGINT NOT NULL DEFAULT 0 COMMENT '禁言到该时间点(毫秒时间戳),0=未禁言';

-- 入群申请记录。只有 join_mode=APPROVAL 的群才会产生这张表的数据
-- (OPEN 模式下直接加进 t_group_member,不经过这里)。
CREATE TABLE t_group_join_request (
    request_id  BIGINT       NOT NULL COMMENT '申请ID,idgen 生成',
    group_id    BIGINT       NOT NULL COMMENT '目标群ID',
    user_id     BIGINT       NOT NULL COMMENT '申请人用户ID',
    status      INT          NOT NULL DEFAULT 0 COMMENT '0=PENDING,1=ACCEPTED,2=REJECTED,对应 GroupJoinRequestStatus',
    greeting    VARCHAR(255) NULL     COMMENT '申请留言',
    created_at  BIGINT       NOT NULL COMMENT '申请时间,毫秒时间戳',
    handled_at  BIGINT       NULL     COMMENT '处理时间,未处理为 NULL',
    handled_by  BIGINT       NULL     COMMENT '处理人(群主/管理员)用户ID',
    PRIMARY KEY (request_id),
    KEY idx_group_status (group_id, status),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入群申请';
