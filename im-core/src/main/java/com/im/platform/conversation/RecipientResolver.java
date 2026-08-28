package com.im.platform.conversation;

import com.im.platform.biz.domain.group.Group;

import java.util.List;
import java.util.Optional;

/**
 * 把 chat_id 解析成"这条消息应该推给哪些人"。单聊/群聊的判断逻辑封装在实现里,
 * 调用方(消息推送路由)不用关心 chat_id 背后到底是哪种会话。
 */
public interface RecipientResolver {

    /** 返回值不包含 senderId 自己(自己不用推给自己)。chat_id 未知时返回空列表。 */
    List<Long> resolveRecipients(long chatId, long senderId);

    /**
     * 调用方已经在别处把 chat_id 对应的群(如果是群聊)解析过一次了,直接把结果传进来复用,
     * 不用再让这里重新查一遍数据库——消息发送链路上,群禁言检查、拉黑检查、收件人解析
     * 都要做同一次"这个 chat_id 是不是群聊"判断,分别各查一次数据库是纯粹的浪费。
     * 默认实现忽略这个参数、退回到两参数版本,不强迫其它调用方(没有预先解析过群的场景,
     * 比如撤回消息、已读回执广播)跟着改。
     */
    default List<Long> resolveRecipients(long chatId, long senderId, Optional<Group> preResolvedGroup) {
        return resolveRecipients(chatId, senderId);
    }
}
