package com.sheldon.flowable_demo.util;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseMsgManager {
    // key:登录账号 value:Sse连接
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter connect(String username) {
        SseEmitter emitter = new SseEmitter(0L);
        emitterMap.put(username, emitter);
        emitter.onCompletion(() -> emitterMap.remove(username));
        emitter.onTimeout(() -> emitterMap.remove(username));
        emitter.onError(e -> emitterMap.remove(username));
        return emitter;
    }

    // 推送给指定用户（发起人）
    public void sendMsg(String username, Object data) {
        SseEmitter emitter = emitterMap.get(username);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("newMsg").data(data));
            } catch (IOException e) {
                emitter.complete();
                emitterMap.remove(username);
            }
        }
    }
}