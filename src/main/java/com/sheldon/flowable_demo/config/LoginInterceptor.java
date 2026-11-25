package com.sheldon.flowable_demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Configuration
/**
 * 登录拦截器，拦截所有请求，判断是否登录
 * 1. 如果登录了，放行
 * 2. 如果没有登录，重定向到登录页
 */
public class LoginInterceptor implements WebMvcConfigurer {

    private static final String SESSION_LOGIN_USER = "loginUser";
    private static final String SESSION_POSITIONS  = "positionCodes";
    /**
     * URL 前缀 → 访问所需岗位编码。
     * 用 LinkedHashMap 只为保证遍历顺序可预期，实际按「最长前缀优先」匹配。
     * 岗位编码取自 sys_position.code，先用岗位管理页确认实际值。
     */
    private static final Map<String, String> URL_POSITION_RULES = new LinkedHashMap<>();
    static {
        URL_POSITION_RULES.put("/position/", "POS_ADMIN");   // 岗位管理：仅系统管理员可进
        URL_POSITION_RULES.put("/user/",     "POS_ADMIN");   // 成员管理：同上
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthHandlerInterceptor())
                .addPathPatterns("/**")
                // 静态资源放行，避免 session 过期时 vendor 下的 js/css 被 302 成登录页
                .excludePathPatterns("/login/**", "/noPermission", "/css/**", "/js/**", "/captcha",
                        "/vendor/**", "/static/**", "/favicon.svg", "/metrics.html", "/error",
                        // 岗位字典只含编码与名称，供设计器「候选岗位」下拉使用。
                        // 只放行这一个只读接口，岗位管理页与写接口仍要求登录。
                        "/position/list");
    }

    static class AuthHandlerInterceptor implements HandlerInterceptor {

            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                HttpSession session = request.getSession();
                // ① 登录校验：没登录一律去登录页
                Object loginUser = request.getSession().getAttribute("loginUser");
                if (loginUser == null) {
                    // 必须拼上 contextPath，否则部署在 /apps/flowable 等子路径下会被跳到域名根
                    response.sendRedirect(request.getContextPath() + "/login/toPage");
                    return false;
                }

                // ② 岗位权限校验：只有命中规则的 URL 才查
                String requiredPosition = matchRequiredPosition(
                        request.getRequestURI(), request.getContextPath());
                if (requiredPosition != null) {
                    @SuppressWarnings("unchecked")
                    Set<String> positionCodes = (Set<String>) session.getAttribute(SESSION_POSITIONS);
                    if (positionCodes == null || !positionCodes.contains(requiredPosition)) {
                        deny(request, response, requiredPosition);
                        return false;
                    }
                }
                return true;
            }
        /** 去掉 contextPath 后按「最长前缀」匹配，返回所需岗位编码；无需权限返回 null */
        private String matchRequiredPosition(String uri, String contextPath) {
            String path = uri;
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
            String matched = null;
            int longest = -1;
            for (Map.Entry<String, String> e : URL_POSITION_RULES.entrySet()) {
                if (path.startsWith(e.getKey()) && e.getKey().length() > longest) {
                    matched = e.getValue();
                    longest = e.getKey().length();
                }
            }
            return matched;
        }

        /** 区分 AJAX 与页面请求：AJAX 返回 JSON，页面重定向到友好的无权限提示页 */
        private void deny(HttpServletRequest request, HttpServletResponse response,
                          String requiredPosition) throws IOException {
            if (isAjax(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"success\":false,\"message\":\"无权访问，需要岗位：" + requiredPosition + "\"}");
            } else {
                // 去掉 contextPath，供提示页判断是哪个菜单；再拼回带 contextPath 的跳转地址
                String path = request.getRequestURI();
                String ctx = request.getContextPath();
                if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
                    path = path.substring(ctx.length());
                }
                response.sendRedirect((ctx == null ? "" : ctx) + "/noPermission"
                        + "?required=" + requiredPosition
                        + "&path=" + java.net.URLEncoder.encode(path, "UTF-8"));
            }
        }

        private boolean isAjax(HttpServletRequest request) {
            if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
                return true;
            }
            String accept = request.getHeader("Accept");
            return accept != null && accept.contains("application/json");
        }
    }
}