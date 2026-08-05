package com.im.platform.sync.service;

/**
 * UpdateLogEntity.eventType 取值,SyncMessageNotifier(写入)和 SyncGrpcService(读取时判断
 * 是否需要按 ack 状态过滤)两边都要用同一套编号,提出来避免两处各写一份魔数。
 */
public final class SyncEventTypes {

    private SyncEventTypes() {
    }

    public static final int NEW_MESSAGE = 0;
    /** payload 格式见 ReadCursorService:"chatId:readerId:readToMessageId"。 */
    public static final int READ_CURSOR_UPDATED = 1;
    /** payload 格式见 ConversationSettingService:"chatId:muted:pinned"。纯个人偏好,只写给操作者自己的其它设备,不广播给会话里的其他参与者。 */
    public static final int CONVERSATION_SETTING_UPDATED = 2;
}
