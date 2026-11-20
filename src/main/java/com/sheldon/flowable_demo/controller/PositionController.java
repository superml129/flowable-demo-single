package com.sheldon.flowable_demo.controller;

import com.sheldon.flowable_demo.common.AuditEventType;
import com.sheldon.flowable_demo.mq.FlowEventProducer;
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
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @Resource
    private FlowEventProducer producer;
    @Resource
    private org.flowable.engine.IdentityService identityService;

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
     * 保存成功后自动同步到 Flowable 引擎表。
     * 岗位管理页面只写业务表（sys_position / sys_user_position），而流程判断
     * "用户是否属于候选组"查的是 ACT_ID_GROUP / ACT_ID_MEMBERSHIP——
     * 不同步的话，流程里的 candidateGroups 永远匹配不到人，待办会变孤儿任务。
     * 同步失败不影响保存结果，可稍后手动点「同步到引擎」重试。
     */
    private void syncQuiet() {
        try {
            positionGroupSyncRunner.sync();
        } catch (Exception e) {
            log.error("岗位数据已保存，但同步到 Flowable 失败，请稍后在页面点「同步到引擎」重试", e);
        }
    }

    /**
     * 新增或修改岗位：id 为空走新增，否则走修改
     */
    @PostMapping("/save")
    @ResponseBody
    public Map<String, Object> save(@RequestBody SysPositionService.PositionForm form, HttpSession session) {
        try {
            String id = form.getId();
            if (id == null || id.trim().isEmpty()) {
                String newId = sysPositionService.create(form);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", newId);
                syncQuiet();
                // 发送岗位变更审计事件
                String msgContent = "新增岗位 " + form.getName() + "（" + newId + "）";
                producer.sendAudit(AuditEventType.AUDIT_POSITION_CHANGE.getEventType(),
                        (String) session.getAttribute("loginUser"), msgContent);
                return ok(data);
            }
            sysPositionService.update(id, form);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            syncQuiet();
            // 岗位停用/改名等本身变化：广播踢该岗位全部现有成员，重新登录拿到新状态
            producer.sendPositionChanged("POSITION_CHANGE", id);
            // 发送岗位变更审计事件
            String msgContent = "修改岗位 " + form.getName() + "（" + form.getCode() + "）";
            producer.sendAudit(AuditEventType.AUDIT_POSITION_CHANGE.getEventType(),
                    (String) session.getAttribute("loginUser"), msgContent);
            return ok(data);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            log.error("保存岗位失败", e);
            return fail("保存失败：" + e.getMessage());
        }
    }

    /**
     * 删除岗位（连带删除成员关系），并同步删除 Flowable 引擎里对应的候选组
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable String id, HttpSession session) {
        try {
            SysPositionService.PositionRow row = sysPositionService.findById(id);
            // 删除前先记下现有成员：删除后成员关系也没了，无法再查出来踢下线
            List<SysPositionService.PositionUser> members = sysPositionService.listUsersOfPosition(id);
            sysPositionService.delete(id);
            // 业务岗位删了，引擎里的组必须跟着删，否则 BPMN 里配了这个候选组
            // 的节点仍然可能派任务给一个"幽灵组"（组内关系随 deleteGroup 一并清除）
            if (row != null && row.getCode() != null && !row.getCode().trim().isEmpty()) {
                try {
                    if (identityService.createGroupQuery().groupId(row.getCode()).singleResult() != null) {
                        identityService.deleteGroup(row.getCode());
                    }
                } catch (Exception e) {
                    log.warn("删除 Flowable 候选组 {} 失败，可手动清理", row.getCode(), e);
                }
            }
            // 被移出岗位的用户重新登录后才失去该岗位权限，逐个广播踢下线
            for (SysPositionService.PositionUser m : members) {
                if (m.getUserId() != null) {
                    producer.sendPositionChanged(m.getUserId(), "MEMBER_CHANGE");
                }
            }
            // 发送岗位变更审计事件埋点
            String msgContent = "删除岗位 " + id + "及其成员（" + members.stream().map(SysPositionService.PositionUser::getUserId).collect(Collectors.toList()) + "）";
            producer.sendAudit(AuditEventType.AUDIT_POSITION_CHANGE.getEventType(),
                    (String) session.getAttribute("loginUser"), msgContent);
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
                                           @RequestBody Map<String, Object> body,
                                           HttpSession session) {
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
            // 保存前先记下原成员，便于事后把「被移出」的人也广播到
            List<Integer> beforeIds = new ArrayList<>();
            for (SysPositionService.PositionUser u : sysPositionService.listUsersOfPosition(id)) {
                if (u.getUserId() != null) {
                    beforeIds.add(u.getUserId());
                }
            }
            sysPositionService.saveMembers(id, userIds, primaryId);
            syncQuiet();
            // 广播：原成员 ∪ 新成员，覆盖「被移出」「被加入」「主责变更」三种情况。
            // 每人一条 MEMBER_CHANGE，消费端逐个踢下线。
            java.util.LinkedHashSet<Integer> affected = new java.util.LinkedHashSet<>(beforeIds);
            affected.addAll(userIds);
            for (Integer uid : affected) {
                if (uid != null) {
                    producer.sendPositionChanged(uid, "MEMBER_CHANGE");
                }
            }
            // 发送岗位变更审计事件
            String msgContent = "岗位 " + id + "成员变更，老成员 " + beforeIds + "，新成员 " + userIds;
            producer.sendAudit(AuditEventType.AUDIT_POSITION_CHANGE.getEventType(),
                    (String) session.getAttribute("loginUser"), msgContent);
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
            return "同步完成：新建组 " + r[0] + " 个，新增成员关系 " + r[1]
                    + " 条，清理失效成员关系 " + r[2] + " 条";
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
