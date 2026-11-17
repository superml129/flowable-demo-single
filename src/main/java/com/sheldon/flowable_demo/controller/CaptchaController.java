package com.sheldon.flowable_demo.controller;

import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class CaptchaController {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // key 前缀与有效期
    public static final String CAPTCHA_KEY_PREFIX = "captcha:";
    public static final long CAPTCHA_TTL_MINUTES = 5;

    // 验证码存入session的key
//    public static final String CAPTCHA_SESSION_KEY = "CAPTCHA_CODE";

    @GetMapping("/captcha")
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 设置响应头
        response.setContentType("image/png");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expire", 0);

        SpecCaptcha captcha;
        String code;
        // 循环生成，直到验证码字符串里面不包含'4'
        do {
            // 宽、高、字符数量，你原来的值不变
            captcha = new SpecCaptcha(130,48,4);
            captcha.setCharType(Captcha.TYPE_ONLY_CHAR);
            code = captcha.text();
        }while(code.contains("4")); // 如果包含4，重新生成

//        HttpSession session = request.getSession();
//        // 把验证码文字存到session
//        session.setAttribute(CAPTCHA_SESSION_KEY, code);

        // 生成验证码ID，写入 Redis，5分钟过期
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                CAPTCHA_KEY_PREFIX + captchaId, code,
                CAPTCHA_TTL_MINUTES, TimeUnit.MINUTES);
        // captchaId 放进 Cookie，随表单提交自动带回，前端页面零改动
        Cookie cookie = new Cookie("captchaId", captchaId);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(300);   // 与 TTL 一致
        response.addCookie(cookie);

        // 输出图片到浏览器
        captcha.out(response.getOutputStream());
    }
}