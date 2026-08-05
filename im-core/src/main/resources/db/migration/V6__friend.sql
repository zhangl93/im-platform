-- 好友关系:申请(t_friend_request)-> 同意后各自一行的双向好友记录(t_friend)。
-- t_friend 不是一行代表一对关系,是"user_id 视角下的通讯录",A、B 成为好友后各自有
-- 一行(user_id=A,friend_id=B)和(user_id=B,friend_id=A)——这样允许各自设置自己的
-- 备注名(remark),A 给 B 起的备注跟 B 给 A 起的备注是两回事,单行存不下这个语义。

CREATE TABLE t_friend_request (
    request_id   BIGINT       NOT NULL,
    from_user_id BIGINT       NOT NULL COMMENT '发起申请的用户',
    to_user_id   BIGINT       NOT NULL COMMENT '被申请的用户',
    status       INT          NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已同意 2=已拒绝',
    greeting     VARCHAR(255) NULL COMMENT '申请附言',
    created_at   BIGINT       NOT NULL,
    handled_at   BIGINT       NULL COMMENT '被同意/拒绝的时间,待处理时为 NULL',
    PRIMARY KEY (request_id),
    KEY idx_to_user_status (to_user_id, status),
    KEY idx_from_user_status (from_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请';

CREATE TABLE t_friend (
    user_id    BIGINT        NOT NULL,
    friend_id  BIGINT        NOT NULL,
    remark     VARCHAR(64)   NULL COMMENT '这个用户给对方起的备注名,只在自己这一侧生效',
    ex         VARCHAR(1024) NULL COMMENT '扩展字段,业务自定义,平台不解析',
    created_at BIGINT        NOT NULL,
    PRIMARY KEY (user_id, friend_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系(单向存储,成为好友时双方各插一行)';
