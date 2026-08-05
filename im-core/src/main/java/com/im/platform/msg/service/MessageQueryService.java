package com.im.platform.msg.service;

import com.im.platform.msg.entity.MessageEntity;
import com.im.platform.msg.store.MessageStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息查询路径:全部按 chat_id 路由到具体分片,避免跨分片扇出查询。
 * 跨会话检索(全文搜索)不走这里,走 Elasticsearch 独立索引。
 */
@Service
public class MessageQueryService {

    private final MessageStore messageStore;

    public MessageQueryService(MessageStore messageStore) {
        this.messageStore = messageStore;
    }

    public List<MessageEntity> pullHistory(long chatId, long beforeMessageId, int limit) {
        return messageStore.pullHistory(chatId, beforeMessageId, limit);
    }
}
