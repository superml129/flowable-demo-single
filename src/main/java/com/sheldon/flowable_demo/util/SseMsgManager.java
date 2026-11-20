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
        sendEvent(username, "newMsg", data);
    }

    /**
     * 通用事件推送：不同业务用不同的事件名，前端 addEventListener 对应监听。
     * @param eventName SSE 事件名，如 newMsg / forceLogout
     */
    public void sendEvent(String username, String eventName, Object data) {
        SseEmitter emitter = emitterMap.get(username);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                emitter.complete();
                emitterMap.remove(username);
            }
        }
    }

    /**
     * 通知被踢下线的浏览器跳转登录页（场景③）。
     * 注意：删 Redis 会话只是服务端失效，浏览器不发起请求就感知不到；
     * 靠这条 SSE 事件才能做到「不点击也自动退出」。
     */
    public void sendForceLogout(String username) {
        sendEvent(username, "forceLogout", "您的账号权限已变更，会话已失效，请重新登录");
    }
}