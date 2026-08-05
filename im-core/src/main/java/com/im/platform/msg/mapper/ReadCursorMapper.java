package com.im.platform.msg.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 联合主键(chat_id, user_id),不继承 BaseMapper——它的 insert/updateById 都假设单列 @TableId,
 * upsert 语义(取 GREATEST 防止游标倒退)用原生 SQL 更直接。
 */
@Mapper
public interface ReadCursorMapper {

    @Insert("INSERT INTO t_read_cursor (chat_id, user_id, read_to_message_id, updated_at) "
            + "VALUES (#{chatId}, #{userId}, #{readToMessageId}, #{updatedAt}) "
            + "ON DUPLICATE KEY UPDATE "
            + "read_to_message_id = GREATEST(read_to_message_id, VALUES(read_to_message_id)), "
            + "updated_at = IF(VALUES(read_to_message_id) > read_to_message_id, VALUES(updated_at), updated_at)")
    void upsert(@Param("chatId") long chatId, @Param("userId") long userId,
                @Param("readToMessageId") long readToMessageId, @Param("updatedAt") long updatedAt);

    @Select("SELECT read_to_message_id FROM t_read_cursor WHERE chat_id = #{chatId} AND user_id = #{userId}")
    Long selectReadToMessageId(@Param("chatId") long chatId, @Param("userId") long userId);
}
