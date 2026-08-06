package com.im.platform.push.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 联合主键(user_id, device_id),不继承 BaseMapper——跟 ConversationSettingMapper/ReadCursorMapper
 * 同样的理由,upsert 语义用原生 SQL 更直接。
 */
@Mapper
public interface PushTokenMapper {

    @Insert("INSERT INTO t_push_token (user_id, device_id, platform, push_token, updated_at) "
            + "VALUES (#{userId}, #{deviceId}, #{platform}, #{pushToken}, #{updatedAt}) "
            + "ON DUPLICATE KEY UPDATE platform = VALUES(platform), push_token = VALUES(push_token), "
            + "updated_at = VALUES(updated_at)")
    void upsert(@Param("userId") long userId, @Param("deviceId") String deviceId,
                @Param("platform") String platform, @Param("pushToken") String pushToken,
                @Param("updatedAt") long updatedAt);

    @Delete("DELETE FROM t_push_token WHERE user_id = #{userId} AND device_id = #{deviceId}")
    void delete(@Param("userId") long userId, @Param("deviceId") String deviceId);

    @Select("SELECT device_id, platform, push_token, updated_at FROM t_push_token WHERE user_id = #{userId}")
    List<PushTokenRow> selectAllForUser(@Param("userId") long userId);

    class PushTokenRow {
        private String deviceId;
        private String platform;
        private String pushToken;
        private Long updatedAt;

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getPushToken() {
            return pushToken;
        }

        public void setPushToken(String pushToken) {
            this.pushToken = pushToken;
        }

        public Long getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(Long updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
