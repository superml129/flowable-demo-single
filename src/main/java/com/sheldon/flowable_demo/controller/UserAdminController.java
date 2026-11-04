package com.sheldon.flowable_demo.controller;

import com.sheldon.flowable_demo.service.SysUserAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成员（系统用户）管理：
 * - 页面：/user/page
 * - 接口：/user/rows、/user/{id}、/user/save、DELETE /user/{id}、/user/{id}/positions
 *
 * 安全约定：任何返回给前端的响应都不包含密码明文，password 字段统一置空。
 */
@Slf4j
@Controller
@RequestMapping("/user")
public class UserAdminController {

    @Resource
    private SysUserAdminService sysUserAdminService;

    // ==================== 页面 ====================

    @GetMapping("/page")
    public String page() {
        return "user/userList";
    }

    // ==================== 接口 ====================

    /**
     * 用户列表，支持关键字与状态筛选
     */
    @GetMapping("/rows")
    @ResponseBody
    public List<Map<String, Object>> rows(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status) {
        try {
            List<SysUserAdminService.UserRow> list = sysUserAdminService.listUsers(keyword, status);
            List<Map<String, Object>> out = new ArrayList<>();
            for (SysUserAdminService.UserRow u : list) {
                out.add(toView(u));
            }
            return out;
        } catch (Exception e) {
            log.error("查询用户列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 用户详情（含所属岗位）
     */
    @GetMapping("/{id}")
    @ResponseBody
    public Map<String, Object> detail(@PathVariable Integer id) {
        SysUserAdminService.UserRow row = sysUserAdminService.findById(id);
        if (row == null) {
            return fail("用户不存在");
        }
        Map<String, Object> data = toView(row);
        List<SysUserAdminService.UserPosition> ps = sysUserAdminService.listPositionsOfUser(id);
        List<Map<String, Object>> posList = new ArrayList<>();
        for (SysUserAdminService.UserPosition p : ps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.code);
            m.put("name", p.name);
            m.put("isPrimary", p.isPrimary);
            posList.add(m);
        }
        data.put("positions", posList);
        return ok(data);
    }

    /**
     * 新增或修改用户（id 为空走新增；password 为空表示不修改）
     */
    @PostMapping("/save")
    @ResponseBody
    public Map<String, Object> save(@RequestBody SysUserAdminService.UserForm form) {
        try {
            if (form.getId() == null) {
                Integer id = sysUserAdminService.create(form);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", id);
                return ok(data);
            }
            sysUserAdminService.update(form.getId(), form);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", form.getId());
            return ok(data);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            log.error("保存用户失败", e);
            return fail("保存失败：" + e.getMessage());
        }
    }

    /**
     * 删除用户（连带删除其岗位归属）
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable Integer id) {
        try {
            sysUserAdminService.delete(id);
            return ok(null);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除用户失败", e);
            return fail("删除失败：" + e.getMessage());
        }
    }

    /**
     * 查用户所属岗位
     */
    @GetMapping("/{id}/positions")
    @ResponseBody
    public List<Map<String, Object>> positions(@PathVariable Integer id) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            for (SysUserAdminService.UserPosition p : sysUserAdminService.listPositionsOfUser(id)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", p.code);
                m.put("name", p.name);
                m.put("isPrimary", p.isPrimary);
                out.add(m);
            }
        } catch (Exception e) {
            log.error("查询用户岗位失败", e);
        }
        return out;
    }

    /**
     * 全量保存用户岗位归属
     *
     * @param body {codes:[...], primaryCode:"POS_XXX"}
     */
    @PostMapping("/{id}/positions")
    @ResponseBody
    public Map<String, Object> savePositions(@PathVariable Integer id,
                                             @RequestBody Map<String, Object> body) {
        try {
            List<String> codes = new ArrayList<>();
            Object raw = body.get("codes");
            if (raw instanceof List) {
                for (Object o : (List<?>) raw) {
                    if (o != null) {
                        codes.add(o.toString());
                    }
                }
            }
            Object p = body.get("primaryCode");
            String primary = p == null ? null : p.toString();
            sysUserAdminService.savePositions(id, codes, primary);
            return ok(null);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            log.error("保存用户岗位失败", e);
            return fail("保存失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    /**
     * 转成给前端的视图对象：剔除密码
     */
    private static Map<String, Object> toView(SysUserAdminService.UserRow u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id);
        m.put("username", u.username);
        m.put("realName", u.realName);
        m.put("status", u.status);
        m.put("positionCount", u.positionCount);
        // password 一律不下发
        return m;
    }

    private static Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        if (data != null) {
            m.put("data", data);
        }
        return m;
    }

    private static Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
