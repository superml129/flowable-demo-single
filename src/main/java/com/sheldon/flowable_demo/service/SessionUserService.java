package com.sheldon.flowable_demo.service;

import com.sheldon.flowable_demo.util.SseMsgManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Set;

/**
 * Redis Session 登记簿 + 踢下线服务（场景③ 用户/岗位变更广播的落地部分）。
 *
 * 背景：Spring Session 把会话存到 Redis（namespace=flowable），但它的 key 是
 * sessionId 维度，没有「用户名 -> sessionId」的反查索引。要按用户踢下线，
 * 必须自己维护这个映射：
 *
 *   flowable:user-sessions:<username>  = SET { sessionId1, sessionId2, ... }
 *
 * 写入时机：登录成功后 bind()（LoginController 调用）；
 * 清理时机：session 正常销毁（注销/过期）由 SessionUserListener unbind()；
 *          个别残留（Redis 通知延迟等）由 kick() 的 TTL 兜底自动消失。
 *
 * 踢下线原理：直接删掉 Spring Session 的两个 key，会话即失效——
 *   flowable:sessions:<sessionId>          会话实体 hash
 *   flowable:sessions:expires:<sessionId>  过期哨兵
 * 下一次请求 getSession() 时找不到会话，LoginInterceptor 就会把它重定向到登录页，
 * 重新登录后 positionCodes / loginPosition 从库里重新加载，权限立即生效。
 *
 * 注意：所有方法都不抛异常——session 管理失败不应影响登录主流程，
 * 踢下线失败也允许（用户最多旧权限用到 session 自然过期为止）。
 */
@Slf4j
@Service
public class SessionUserService {

    /** Spring Session 的 key 前缀，namespace 配置为 flowable 时的实际结构 */
    private static final String SESSION_KEY_PREFIX     = "flowable:sessions:";
    private static final String SESSION_EXPIRES_PREFIX = "flowable:sessions:expires:";

    /** 自己维护的用户 -> session 索引前缀 */
    private static final String USER_INDEX_PREFIX = "flowable:user-sessions:";

    /** 索引 key 的 TTL：比 session 有效期 30m 略长，防止索引比会话先过期 */
    private static final Duration INDEX_TTL = Duration.ofMinutes(35);

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SseMsgManager sseMsgManager;

    /**
     * 登录成功后登记：username -> sessionId。
     * 一个人可以在多个浏览器/多实例上登录，所以用 SET 存多个 sessionId。
     */
    public void bind(String username, String sessionId) {
        if (username == null || sessionId == null) {
            return;
        }
        try {
            String key = USER_INDEX_PREFIX + username;
            stringRedisTemplate.opsForSet().add(key, sessionId);
            stringRedisTemplate.expire(key, INDEX_TTL);
        } catch (Exception e) {
            log.error("登记 session 索引失败 username={} sessionId={}", username, sessionId, e);
        }
    }

    /**
     * session 销毁（注销/过期）时解除登记。由 SessionUserListener 调用。
     */
    public void unbind(String username, String sessionId) {
        if (username == null || sessionId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().remove(USER_INDEX_PREFIX + username, sessionId);
        } catch (Exception e) {
            log.error("解除 session 索引失败 username={} sessionId={}", username, sessionId, e);
        }
    }

    /**
     * 踢下线：删除该用户当前所有 Redis 会话。
     *
     * @return 实际删除掉的会话数（0 表示该用户当前没有在线会话）
     */
    public int kick(String username) {
        if (username == null) {
            return 0;
        }
        try {
            String indexKey = USER_INDEX_PREFIX + username;
            Set<String> sessionIds = stringRedisTemplate.opsForSet().members(indexKey);
            int removed = 0;
            if (sessionIds != null && !sessionIds.isEmpty()) {
                for (String sessionId : sessionIds) {
                    // 先删过期哨兵再删实体，顺序不影响结果，两个都删才算干净
                    stringRedisTemplate.delete(SESSION_EXPIRES_PREFIX + sessionId);
                    Boolean ok = stringRedisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
                    if (Boolean.TRUE.equals(ok)) {
                        removed++;
                        log.info("已踢下线 username={} sessionId={}", username, sessionId);
                    }
                }
            }
            stringRedisTemplate.delete(indexKey);
            if (removed > 0) {
                log.info("用户 {} 共踢掉 {} 个会话", username, removed);
                // 通过 SSE 推送 forceLogout 事件，让浏览器立即跳转登录页。
                // 不推的话会话虽然已失效，但用户停留在已渲染页面上毫无感知，
                // 要等下一次点击才被拦截器重定向。
                sseMsgManager.sendForceLogout(username);
            }
            return removed;
        } catch (Exception e) {
            log.error("踢下线失败 username={}", username, e);
            return 0;
        }
    }
}
