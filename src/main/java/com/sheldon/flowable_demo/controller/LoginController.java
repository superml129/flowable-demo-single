package com.sheldon.flowable_demo.controller;

import com.sheldon.flowable_demo.entity.SysUser;
import com.sheldon.flowable_demo.service.SysUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/login")
public class LoginController {

    @Resource
    private SysUserService sysUserService;

    // 跳转登录页
    @GetMapping("/toPage")
    public String toLogin() {
        return "login";
    }

    // 登录提交
    @PostMapping("/doLogin")
    public String doLogin(String username, String password, String captcha, HttpSession session, Model model) {
        // 从session取出图片验证码
        String sessionCaptcha = (String) session.getAttribute(CaptchaController.CAPTCHA_SESSION_KEY);
        // 校验：非空 && 忽略大小写对比
        if (sessionCaptcha == null || !sessionCaptcha.equalsIgnoreCase(captcha)) {
            model.addAttribute("msg", "验证码错误");
            return "login";
        }
        // 验证码校验通过后，清除session中的验证码（一次性使用，防止重复提交）
        session.removeAttribute(CaptchaController.CAPTCHA_SESSION_KEY);

        SysUser user = sysUserService.login(username, password);
        if (user == null) {
            model.addAttribute("msg", "账号或密码错误");
            return "login";
        }
        // ✅ 登录成功，把【登录账号】存入session，前端页面 ${loginUser} 继续使用不用改页面！
        session.setAttribute("loginUser", user.getUsername());
        session.setAttribute("password", user.getPassword());
        return "redirect:/home/index";
    }

    // 退出登录
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login/toPage";
    }

    @GetMapping("/login")
    public String loginPage(String error, String logout, HttpSession session, Model model) {
        String msg = (String) session.getAttribute("msg");
        if (msg != null) {
            model.addAttribute("msg", msg);
            session.removeAttribute("msg");
        } else if (error != null) {
            model.addAttribute("msg", "账号或密码错误！");
        }
        if (logout != null) {
            model.addAttribute("msg", "已成功退出登录");
        }

        // 取出暂存的用户名密码
        String tempUsername = (String) session.getAttribute("tempUsername");
        String tempPwd = (String) session.getAttribute("tempPwd");
        model.addAttribute("username", tempUsername);
        model.addAttribute("password", tempPwd);

        // 读取完立刻清除，避免一直缓存
        session.removeAttribute("tempUsername");
        session.removeAttribute("tempPwd");
        return "login";
    }
}