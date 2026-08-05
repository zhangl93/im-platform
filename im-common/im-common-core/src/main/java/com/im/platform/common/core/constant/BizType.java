package com.im.platform.common.core.constant;

/**
 * idgen 服务的发号业务类型划分,不同 biz_type 允许使用不同发号策略
 * (如 message_id 需要编码 chat_id 分片信息,user_id/group_id 走普通雪花号段)。
 */
public final class BizType {

    private BizType() {
    }

    public static final String MESSAGE_ID = "message_id";
    public static final String USER_ID = "user_id";
    public static final String GROUP_ID = "group_id";
    public static final String FILE_ID = "file_id";
    public static final String CHAT_ID = "chat_id";
    public static final String FRIEND_REQUEST_ID = "friend_request_id";
    public static final String GROUP_JOIN_REQUEST_ID = "group_join_request_id";
}
