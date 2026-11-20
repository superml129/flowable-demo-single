package com.sheldon.flowable_demo.config;

import com.sheldon.flowable_demo.service.SessionUserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * session 销毁监听器：会话注销/过期时，把「用户名 -> sessionId」索引解除登记。
 *
 * Spring Boot 会自动把实现了 Servlet 监听器接口的 Bean 注册到容器，
 * 不需要额外的 web.xml 或 @WebListener 扫描配置。
 *
 * 说明：Redis 里会话被动过期时（用户直接关浏览器再没回来），destroyed 事件
 * 由 Spring Session 的过期通知触发，可能有最多一分钟的延迟——残留的失效
 * sessionId 无害，SessionUserService.kick() 删不存在的 key 只是 no-op。
 */
@Component
public class SessionUserListener implements HttpSessionListener {

    private static final String SESSION_LOGIN_USER = "loginUser";

    @Resource
    private SessionUserService sessionUserService;

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        Object loginUser = se.getSession().getAttribute(SESSION_LOGIN_USER);
        if (loginUser != null) {
            sessionUserService.unbind(loginUser.toString(), se.getSession().getId());
        }
    }
}
