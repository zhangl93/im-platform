package com.im.platform.msg.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 联合主键(user_id, chat_id),不继承 BaseMapper——跟 ReadCursorMapper 同样的理由,
 * upsert 语义用原生 SQL 更直接,行不存在时查询返回 null 即代表默认值(不免打扰、不置顶)。
 */
@Mapper
public interface ConversationSettingMapper {

    @Insert("INSERT INTO t_conversation_setting (user_id, chat_id, is_muted, is_pinned, updated_at) "
            + "VALUES (#{userId}, #{chatId}, #{muted}, #{pinned}, #{updatedAt}) "
            + "ON DUPLICATE KEY UPDATE is_muted = VALUES(is_muted), is_pinned = VALUES(is_pinned), "
            + "updated_at = VALUES(updated_at)")
    void upsert(@Param("userId") long userId, @Param("chatId") long chatId,
                @Param("muted") boolean muted, @Param("pinned") boolean pinned,
                @Param("updatedAt") long updatedAt);

    @Select("SELECT chat_id, is_muted, is_pinned, updated_at FROM t_conversation_setting WHERE user_id = #{userId}")
    List<ConversationSettingRow> selectAllForUser(@Param("userId") long userId);

    class ConversationSettingRow {
        private Long chatId;
        private Boolean isMuted;
        private Boolean isPinned;
        private Long updatedAt;

        public Long getChatId() {
            return chatId;
        }

        public void setChatId(Long chatId) {
            this.chatId = chatId;
        }

        public Boolean getIsMuted() {
            return isMuted;
        }

        public void setIsMuted(Boolean isMuted) {
            this.isMuted = isMuted;
        }

        public Boolean getIsPinned() {
            return isPinned;
        }

        public void setIsPinned(Boolean isPinned) {
            this.isPinned = isPinned;
        }

        public Long getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(Long updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
