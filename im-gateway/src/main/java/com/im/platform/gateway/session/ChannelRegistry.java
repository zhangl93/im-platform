package com.im.platform.gateway.session;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 本地 Channel &lt;-&gt; UserId 双向映射,支持一个用户多端同时在线(每个设备一条独立 Channel)。
 *
 * 这是 gateway"无状态,连接层例外"的另一处体现:这份映射只在持有这些连接的 gateway 实例内存里,
 * 不进 Redis——Redis 存的是"这个用户在不在线"这种跨实例都要看到的粗粒度状态(见 im-status),
 * 而"具体是哪条 Channel"只有持有该连接的实例自己用得上,推送消息时逐实例判断"我这有没有",
 * 不需要一个全局的 Channel 位置索引。
 */
@Component
public class ChannelRegistry {

    private final ConcurrentHashMap<Long, Set<Channel>> userChannels = new ConcurrentHashMap<>();

    /** @return 绑定前该用户在本实例上是否已经在线(用来判断要不要调 status.setOnline)。 */
    public boolean bind(long userId, Channel channel) {
        Set<Channel> channels = userChannels.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>());
        boolean wasOnlineLocally = !channels.isEmpty();
        channels.add(channel);
        return wasOnlineLocally;
    }

    /** @return 解绑后该用户在本实例上是否已经完全离线(所有设备都断了,该调 status.setOffline 了)。 */
    public boolean unbind(long userId, Channel channel) {
        Set<Channel> channels = userChannels.get(userId);
        if (channels == null) {
            return true;
        }
        channels.remove(channel);
        if (channels.isEmpty()) {
            userChannels.remove(userId, channels);
            return true;
        }
        return false;
    }

    public Set<Channel> getChannels(long userId) {
        Set<Channel> channels = userChannels.get(userId);
        return channels == null ? Collections.emptySet() : channels;
    }

    public boolean isOnlineLocally(long userId) {
        Set<Channel> channels = userChannels.get(userId);
        return channels != null && !channels.isEmpty();
    }

    /** 当前实例上有多少个不同用户在线,Prometheus 指标用。 */
    public int onlineUserCount() {
        return userChannels.size();
    }

    /** 当前实例上一共有多少条连接(同一用户多端算多条),Prometheus 指标用。 */
    public int onlineConnectionCount() {
        return userChannels.values().stream().mapToInt(Set::size).sum();
    }
}
