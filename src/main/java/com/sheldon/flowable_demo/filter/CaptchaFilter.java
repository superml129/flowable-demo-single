package com.sheldon.flowable_demo.filter;

import com.sheldon.flowable_demo.controller.CaptchaController;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class CaptchaFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 只拦截登录提交接口 /doLogin
        String url = request.getRequestURI();
        if("/doLogin".equals(url) && "post".equalsIgnoreCase(request.getMethod())){
            String captchaParam = request.getParameter("captcha");
            HttpSession session = request.getSession();
            String sessionCode = (String) session.getAttribute(CaptchaController.CAPTCHA_SESSION_KEY);

            if(captchaParam == null || sessionCode == null || !captchaParam.toLowerCase().equals(sessionCode)){
                // 验证码错误，重定向回登录页，携带error标识
                session.removeAttribute(CaptchaController.CAPTCHA_SESSION_KEY);
                session.setAttribute("tempUsername", request.getParameter("username"));
                session.setAttribute("tempPwd", request.getParameter("password"));
                session.setAttribute("msg", "验证码错误");
                response.sendRedirect("/login");
                return;
            }
            // 验证通过，清除session里验证码（一次性）
            session.removeAttribute(CaptchaController.CAPTCHA_SESSION_KEY);
        }
        filterChain.doFilter(request, response);
    }
}