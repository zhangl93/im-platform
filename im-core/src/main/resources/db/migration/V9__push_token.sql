-- 离线推送(APNs/FCM 之类厂商通道)用的设备 token 登记表。一个用户可能同时有多台设备,
-- 主键是 (user_id, device_id),同一台设备重新登记直接覆盖旧 token(设备换绑/token 轮换的
-- 常见场景,不需要走删除再插入)。
CREATE TABLE t_push_token (
    user_id    BIGINT       NOT NULL COMMENT '用户ID',
    device_id  VARCHAR(128) NOT NULL COMMENT '设备ID,跟 NegotiateKey 握手时的 device_id 对应',
    platform   VARCHAR(16)  NOT NULL COMMENT '推送平台:IOS / ANDROID,对应 PushPlatform 枚举',
    push_token VARCHAR(512) NOT NULL COMMENT '厂商推送 token(APNs device token / FCM registration token)',
    updated_at BIGINT       NOT NULL COMMENT '更新时间,毫秒时间戳',
    PRIMARY KEY (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线推送设备 token';
