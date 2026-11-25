package com.sheldon.flowable_demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

/**
 * 无权限时的友好提示页。
 * 由 LoginInterceptor 在页面请求被拒时重定向过来（AJAX 请求仍返回 JSON，不走这里）。
 */
@Controller
public class NoPermissionController {

    @GetMapping("/noPermission")
    public String noPermission(@RequestParam(required = false) String required,
                               @RequestParam(required = false) String path,
                               Model model,
                               HttpSession session) {
        String menu = "该页面";
        if (path != null) {
            if (path.startsWith("/position/")) {
                menu = "岗位管理";
            } else if (path.startsWith("/user/")) {
                menu = "成员管理";
            }
        }
        model.addAttribute("menu", menu);
        model.addAttribute("required", required == null ? "系统管理员" : required);
        // 公共导航 fragment 需要 loginUser
        model.addAttribute("loginUser", session.getAttribute("loginUser"));
        return "noPermission";
    }
}
