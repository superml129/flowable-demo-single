package com.sheldon.flowable_demo.controller;

import com.sheldon.flowable_demo.service.PositionGroupSyncRunner;
import com.sheldon.flowable_demo.service.SysPositionService;
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
 * 岗位管理：
 * - 页面：/position/page
 * - 接口：/position/list、/position/{id}、/position/save、/position/{id}/members ...
 * - 辅助：/position/sync 把岗位同步到 Flowable 候选组、/position/members?code= 查岗位下的人
 *
 * 注意：本路径在 LoginInterceptor 中整体放行（设计器下拉 /position/list 需要无会话可用），
 * 页面与写接口的登录校验由前端与业务层各自保证，此处不重复拦截。
 */
@Slf4j
@Controller
@RequestMapping("/position")
public class PositionController {

    @Resource
    private PositionGroupSyncRunner positionGroupSyncRunner;
    @Resource
    private SysPositionService sysPositionService;

    // ==================== 页面 ====================

    @GetMapping("/page")
    public String page() {
        return "position/positionList";
    }

    // ==================== 接口 ====================

    /**
     * 岗位列表，供流程设计器「候选岗位」下拉使用。
     * 只返回启用岗位，避免停用岗位还能被绑定到流程上。
     */
    @GetMapping("/list")
    @ResponseBody
    public List<SysPositionService.PositionOption> list() {
        try {
            return sysPositionService.listPositions();
        } catch (Exception e) {
            log.error("查询岗位列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 岗位列表（管理页，含停用岗位与成员数），支持关键字模糊查询
     */
    @GetMapping("/rows")
    @ResponseBody
    public List<SysPositionService.PositionRow> rows(@RequestParam(required = false) String keyword) {
        try {
            return sysPositionService.listPositionRows(keyword);
        } catch (Exception e) {
            log.error("查询岗位管理列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 岗位详情
     */
    @GetMapping("/{id}")
    @ResponseBody
    public Map<String, Object> detail(@PathVariable String id) {
        SysPositionService.PositionRow row = sysPositionService.findById(id);
        if (row == null) {
            return fail("岗位不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.id);
        data.put("code", row.code);
        data.put("name", row.name);
        data.put("description", row.description);
        data.put("status", row.status);
        data.put("memberCount", row.memberCount);
        return ok(data);
    }

    /**
     * 新增或修改岗位：id 为空走新增，否则走修改
     */
    @PostMapping("/save")
    @ResponseBody
    public Map<String, Object> save(@RequestBody SysPositionService.PositionForm form) {
        try {
            String id = form.getId();
            if (id == null || id.trim().isEmpty()) {
                String newId = sysPositionService.create(form);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", newId);
                return ok(data);
            }
            sysPositionService.update(id, form);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            return ok(data);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            log.error("保存岗位失败", e);
            return fail("保存失败：" + e.getMessage());
        }
    }

    /**
     * 删除岗位（连带删除成员关系）
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable String id) {
        try {
            sysPositionService.delete(id);
            return ok(null);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除岗位失败", e);
            return fail("删除失败：" + e.getMessage());
        }
    }

    /**
     * 查岗位成员
     */
    @GetMapping("/{id}/members")
    @ResponseBody
    public List<SysPositionService.PositionUser> membersOfPosition(@PathVariable String id) {
        try {
            return sysPositionService.listUsersOfPosition(id);
        } catch (Exception e) {
            log.error("查询岗位成员失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 全量保存岗位成员
     *
     * @param body {userIds:[1,2,3], primaryId:2}
     */
    @PostMapping("/{id}/members")
    @ResponseBody
    public Map<String, Object> saveMembers(@PathVariable String id,
                                           @RequestBody Map<String, Object> body) {
        try {
            List<Integer> userIds = new ArrayList<>();
            Object raw = body.get("userIds");
            if (raw instanceof List) {
                for (Object o : (List<?>) raw) {
                    if (o instanceof Number) {
                        userIds.add(((Number) o).intValue());
                    } else if (o != null) {
                        try {
                            userIds.add(Integer.valueOf(o.toString()));
                        } catch (NumberFormatException ignored) {
                            // 忽略非法值
                        }
                    }
                }
            }
            Integer primaryId = null;
            Object p = body.get("primaryId");
            if (p instanceof Number) {
                primaryId = ((Number) p).intValue();
            } else if (p != null && !p.toString().trim().isEmpty()) {
                try {
                    primaryId = Integer.valueOf(p.toString().trim());
                } catch (NumberFormatException ignored) {
                    // 忽略非法值
                }
            }
            sysPositionService.saveMembers(id, userIds, primaryId);
            return ok(null);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            log.error("保存岗位成员失败", e);
            return fail("保存失败：" + e.getMessage());
        }
    }

    /**
     * 全部启用用户（成员分配候选池）
     */
    @GetMapping("/users")
    @ResponseBody
    public List<SysPositionService.PositionUser> users() {
        try {
            return sysPositionService.listAllUsers();
        } catch (Exception e) {
            log.error("查询用户列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 把岗位同步到 Flowable 的 ACT_ID_GROUP / ACT_ID_MEMBERSHIP
     */
    @GetMapping("/sync")
    @ResponseBody
    public String sync() {
        try {
            int[] r = positionGroupSyncRunner.sync();
            return "同步完成：新建组 " + r[0] + " 个，新增成员关系 " + r[1] + " 条";
        } catch (Exception e) {
            log.error("岗位同步失败", e);
            return "同步失败：" + e.getMessage();
        }
    }

    /**
     * 查某个岗位 code 下的用户名（调试用）
     */
    @GetMapping("/members")
    @ResponseBody
    public List<String> members(String code) {
        return sysPositionService.listUsernamesByPosition(code);
    }

    // ==================== 统一返回体 ====================

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
