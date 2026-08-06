package com.im.platform.msg.store;

import com.im.platform.msg.entity.MessageEntity;

import java.util.List;

/**
 * 消息存储的抽象,唯一实现是 {@link com.im.platform.msg.store.mongo.MongoMessageStore}。
 * 只覆盖消息这一块——用户/群组/会话/已读游标等其它数据仍然固定在 MySQL 上。
 * 保留这层接口(而不是直接在 MessageWriteService/MessageQueryService 里认 MongoTemplate)
 * 是为了让上层单元测试可以 mock 存储、不用起真实 MongoDB。
 */
public interface MessageStore {

    void insert(MessageEntity entity);

    MessageEntity findById(long chatId, long messageId);

    List<MessageEntity> pullHistory(long chatId, long beforeMessageId, int limit);

    /** 这个会话里 message_id 大于 afterMessageId 的消息数量,供未读数计算用。 */
    long countAfter(long chatId, long afterMessageId);

    /** 标记一条消息已撤回。不做物理删除——保留记录,pullHistory 按 recalled 标记返回占位。 */
    void markRecalled(long chatId, long messageId);
}
