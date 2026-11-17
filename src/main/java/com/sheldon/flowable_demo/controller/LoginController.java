package com.sheldon.flowable_demo.controller;

import com.sheldon.flowable_demo.entity.SysUser;
import com.sheldon.flowable_demo.service.SysUserAdminService;
import com.sheldon.flowable_demo.service.SysUserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/login")
public class LoginController {

    @Resource
    private SysUserService sysUserService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SysUserAdminService sysUserAdminService;

    // 跳转登录页
    @GetMapping("/toPage")
    public String toLogin() {
        return "login";
    }

    // 登录提交
    @PostMapping("/doLogin")
    public String doLogin(String username, String password, String captcha,
                          @CookieValue(value = "captchaId", required = false) String captchaId,
                          HttpSession session, Model model) {
        // 三种失败情况分开提示：
        //   1. Redis 里查不到 key（超过5分钟TTL / 已被上次提交消费 / 浏览器没带上captchaId Cookie）
        //      → 「已过期或已使用，请刷新」
        //   2. key 存在但内容对不上 → 「验证码错误」（用户真的输错了）
        //   3. Redis 连接异常 → 「验证码服务暂不可用」
        String redisCaptcha = null;
        String captchaKey = null;
        if (captchaId != null) {
            captchaKey = CaptchaController.CAPTCHA_KEY_PREFIX + captchaId;
            try {
                redisCaptcha = stringRedisTemplate.opsForValue().get(captchaKey);
            } catch (Exception e) {
                // Redis 挂了/没启动：给出明确提示而不是裸 500
                model.addAttribute("msg", "验证码服务暂不可用，请稍后再试");
                return "login";
            }
        }
        if (redisCaptcha == null) {
            // key 不存在：TTL 已过（默认5分钟）、或已被上一次提交消费（一次性）
            model.addAttribute("msg", "验证码已过期或已使用，请点击图片刷新后重试");
            return "login";
        }
        // 无论输对输错都立即删除（一次性，防止拿同一验证码反复暴力尝试）
        stringRedisTemplate.delete(captchaKey);
        if (!redisCaptcha.equalsIgnoreCase(captcha)) {
            model.addAttribute("msg", "验证码错误");
            return "login";
        }

//        // 从session取出图片验证码
//        String sessionCaptcha = (String) session.getAttribute(CaptchaController.CAPTCHA_SESSION_KEY);
//        // 校验：非空 && 忽略大小写对比
//        if (sessionCaptcha == null || !sessionCaptcha.equalsIgnoreCase(captcha)) {
//            model.addAttribute("msg", "验证码错误");
//            return "login";
//        }
//        // 验证码校验通过后，清除session中的验证码（一次性使用，防止重复提交）
//        session.removeAttribute(CaptchaController.CAPTCHA_SESSION_KEY);



        SysUser user = sysUserService.login(username, password);
        if (user == null) {
            model.addAttribute("msg", "账号或密码错误");
            return "login";
        }
        // ✅ 登录成功，把【登录账号】存入session，前端页面 ${loginUser} 继续使用不用改页面！
        session.setAttribute("loginUser", user.getUsername());
        session.setAttribute("password", user.getPassword());
        // 查岗位编码集合，存 session（登录后不再每次请求查库）
        Set<String> positions = sysUserAdminService.listPositionsOfUser(user.getId())
                .stream().map(SysUserAdminService.UserPosition::getCode).collect(Collectors.toSet());
        session.setAttribute("positionCodes", positions);

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