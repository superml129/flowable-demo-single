package com.sheldon.flowable_demo.controller;

import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/leaveApply")
public class LeaveApplyController {

    @Resource
    private RuntimeService runtimeService;

    @Resource
    private TaskService taskService;

    // 跳转到提交表单页面
    @RequestMapping("/toPage")
    public String toApplyPage(){
        return "leaveApply";
    }

    // 提交表单，启动流程
    @PostMapping("/submit")
    public String submitLeave(HttpSession session,
                              @RequestParam String applyUser,
                              @RequestParam Integer leaveDays,
                              @RequestParam String leaveReason,
                              @RequestParam(required = false) String approvePosition,
                              RedirectAttributes redirectAttributes){

        Map<String,Object> variables = new HashMap<>();
        variables.put("applyUser",applyUser);
        variables.put("leaveDays",leaveDays);
        variables.put("leaveReason",leaveReason);
        // 岗位编码：仅作流程变量留痕，节点绑定关系写在 BPMN 的 flowable:candidateGroups 里
        if (approvePosition != null) {
            variables.put("approvePosition", approvePosition);
        }

        // 关键：启动前设置认证用户，Flowable 才会记录 startUserId（流程发起人），
        // 否则全局任务完成通知监听器拿不到发起人，无法推送消息
        String loginUser = (String) session.getAttribute("loginUser");
        Authentication.setAuthenticatedUserId(loginUser != null ? loginUser : applyUser);
        String instanceId;
        try {
            // 启动流程，key对应bpmn里面的id
            instanceId = runtimeService.startProcessInstanceByKey("leaveProcess", variables).getId();
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }

        // 查询刚刚产生的下一步待办任务（候选组任务的 assignee 为空，需要展示候选组）
        Task task = taskService.createTaskQuery()
                .processInstanceId(instanceId)
                .singleResult();

        String nextAssignee = "";
        if(task != null){
            if (task.getAssignee() != null) {
                nextAssignee = task.getAssignee();
            } else {
                // 未认领的岗位待办：打印候选组
                List<org.flowable.identitylink.api.IdentityLink> links =
                        taskService.getIdentityLinksForTask(task.getId());
                List<String> groups = new ArrayList<>();
                for (org.flowable.identitylink.api.IdentityLink l : links) {
                    if ("candidate".equals(l.getType()) && l.getGroupId() != null) {
                        groups.add(l.getGroupId());
                    }
                }
                nextAssignee = groups.isEmpty() ? "（待岗位认领）" : "（岗位：" + String.join(",", groups) + "）";
            }
        }

        String msg = "请假申请提交成功！流程实例ID："+instanceId + "，下一步处理岗位/人员："+ nextAssignee;
        redirectAttributes.addFlashAttribute("msg",msg);
        return "redirect:/leaveApply/toPage";
    }
}
