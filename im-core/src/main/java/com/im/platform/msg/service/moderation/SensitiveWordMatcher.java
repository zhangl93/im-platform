package com.im.platform.msg.service.moderation;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 敏感词本地内存匹配(Trie/DFA)。moderation 服务只负责词库来源与版本管理,
 * 真正的逐字匹配下沉到 msg 服务本地内存执行,避免每条消息一次跨服务同步调用。
 *
 * 词库通过 {@link #reload(List)} 整体替换(原子引用切换),由定时任务/事件驱动的规则拉取器调用,
 * 拉取逻辑待接入 moderation.proto 生成的 stub 后补充。
 */
@Component
public class SensitiveWordMatcher {

    private static final char END_FLAG = '\0';

    private final AtomicReference<Map<Character, Object>> trieRoot =
            new AtomicReference<>(new HashMap<>());

    @SuppressWarnings("unchecked")
    public void reload(List<String> words) {
        Map<Character, Object> root = new HashMap<>();
        for (String word : words) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            Map<Character, Object> node = root;
            for (char c : word.toCharArray()) {
                node = (Map<Character, Object>) node.computeIfAbsent(c, k -> new HashMap<>());
            }
            node.put(END_FLAG, Boolean.TRUE);
        }
        trieRoot.set(root);
    }

    @SuppressWarnings("unchecked")
    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        Map<Character, Object> root = trieRoot.get();
        int len = text.length();
        for (int start = 0; start < len; start++) {
            Map<Character, Object> node = root;
            for (int i = start; i < len; i++) {
                Object next = node.get(text.charAt(i));
                if (next == null) {
                    break;
                }
                node = (Map<Character, Object>) next;
                if (Boolean.TRUE.equals(node.get(END_FLAG))) {
                    return true;
                }
            }
        }
        return false;
    }
}
