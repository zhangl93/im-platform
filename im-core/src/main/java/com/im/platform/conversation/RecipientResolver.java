package com.im.platform.conversation;

import java.util.List;

/**
 * 把 chat_id 解析成"这条消息应该推给哪些人"。单聊/群聊的判断逻辑封装在实现里,
 * 调用方(消息推送路由)不用关心 chat_id 背后到底是哪种会话。
 */
public interface RecipientResolver {

    /** 返回值不包含 senderId 自己(自己不用推给自己)。chat_id 未知时返回空列表。 */
    List<Long> resolveRecipients(long chatId, long senderId);
}
