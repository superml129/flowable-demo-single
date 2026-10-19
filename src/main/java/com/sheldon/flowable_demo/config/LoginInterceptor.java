package com.sheldon.flowable_demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
/**
 * 登录拦截器，拦截所有请求，判断是否登录
 * 1. 如果登录了，放行
 * 2. 如果没有登录，重定向到登录页
 */
public class LoginInterceptor implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                        Object loginUser = request.getSession().getAttribute("loginUser");
                        if (loginUser == null) {
                            response.sendRedirect("/login/toPage");
                            return false;
                        }
                        return true;
                    }
                })
                .addPathPatterns("/**")
                .excludePathPatterns("/login/**", "/css/**", "/captcha");
    }
}