package com.im.platform.msg.entity;

import java.util.List;

/**
 * 消息领域对象,存储介质无关(实际落库是 MongoDB,见 com.im.platform.msg.store.mongo.MessageDocument),
 * MessageStore 接口两侧(service 层调用方 / MongoMessageStore 内部转换)都用这个类做载体。
 */
public class MessageEntity {

    private Long messageId; // idgen 生成

    private Long chatId;
    private Long senderId;
    private byte[] content;
    private Integer msgType;
    private Long serverTime;
    private String clientMsgId;
    private String ex; // 扩展字段(消息级自定义数据,比如表情回应),业务自定义,平台不解析
    private Boolean recalled = Boolean.FALSE;
    private List<Long> atUserIds; // 群消息 @ 了哪些人,平台只原样存取转发,不校验是否真的是群成员

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public Integer getMsgType() {
        return msgType;
    }

    public void setMsgType(Integer msgType) {
        this.msgType = msgType;
    }

    public Long getServerTime() {
        return serverTime;
    }

    public void setServerTime(Long serverTime) {
        this.serverTime = serverTime;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public Boolean getRecalled() {
        return recalled;
    }

    public void setRecalled(Boolean recalled) {
        this.recalled = recalled;
    }

    public List<Long> getAtUserIds() {
        return atUserIds;
    }

    public void setAtUserIds(List<Long> atUserIds) {
        this.atUserIds = atUserIds;
    }
}
