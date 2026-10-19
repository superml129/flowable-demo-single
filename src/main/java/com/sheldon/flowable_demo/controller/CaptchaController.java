package com.sheldon.flowable_demo.controller;

import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@RestController
public class CaptchaController {

    // 验证码存入session的key
    public static final String CAPTCHA_SESSION_KEY = "CAPTCHA_CODE";

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

        HttpSession session = request.getSession();
        // 把验证码文字存到session
        session.setAttribute(CAPTCHA_SESSION_KEY, code);

        // 输出图片到浏览器
        captcha.out(response.getOutputStream());
    }
}