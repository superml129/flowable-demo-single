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

    // 跳转到提交表单页面（发起人=当前登录人，页面只读展示）
    @RequestMapping("/toPage")
    public String toApplyPage(){
        return "leaveApply";
    }

    // 提交表单，启动流程
    @PostMapping("/submit")
    public String submitLeave(HttpSession session,
                              @RequestParam Integer leaveDays,
                              @RequestParam String leaveReason,
                              RedirectAttributes redirectAttributes){

        // 发起人一律取当前登录人：页面上的发起人是只读展示，这里也不再接收表单参数，
        // 避免任何绕过页面直接构造请求的冒名提交
        String loginUser = (String) session.getAttribute("loginUser");
        if (loginUser == null || loginUser.isBlank()) {
            redirectAttributes.addFlashAttribute("errMsg", "登录状态已失效，请重新登录后再提交申请");
            return "redirect:/login/toPage";
        }

        Map<String,Object> variables = new HashMap<>();
        variables.put("applyUser", loginUser);
        variables.put("leaveDays",leaveDays);
        variables.put("leaveReason",leaveReason);

        // 关键：启动前设置认证用户，Flowable 才会记录 startUserId（流程发起人），
        // 否则全局任务完成通知监听器拿不到发起人，无法推送消息
        Authentication.setAuthenticatedUserId(loginUser);
        String instanceId;
        try {
            // 启动流程，key对应bpmn里面的id。
            // 审批节点在 BPMN 里绑定了 candidateGroups（岗位），
            // 流转到哪个节点由流程定义决定，这里不需要（也不应该）指定岗位/审批人
            instanceId = runtimeService.startProcessInstanceByKey("leaveProcess", variables).getId();

            // 发起即提交：流程的第一个节点「发起申请」就是页面上的这次提交动作本身，
            // 由系统代表发起人立即完成（写入"提交"履历），待办不落在发起人名下，
            // 而是直接落到下一节点绑定的岗位（如部门经理审批 → POS_DEPT_MANAGER）。
            // 只认发起节点（按节点 ID 或节点名匹配），其它结构变化不受影响；
            // 若自动完成失败，任务停留在原节点，等同于旧行为，不会丢流程。
            Task initTask = taskService.createTaskQuery()
                    .processInstanceId(instanceId)
                    .singleResult();
            boolean isInitiateNode = initTask != null
                    && ("Activity_1b4opc7".equals(initTask.getTaskDefinitionKey())
                        || "发起申请".equals(initTask.getName()));
            if (isInitiateNode) {
                taskService.addComment(initTask.getId(), instanceId, "[提交] 提交请假申请");
                // 先认领再完成：履历中该节点的办理人显示为发起人本人
                taskService.claim(initTask.getId(), loginUser);
                taskService.complete(initTask.getId());
            }
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }

        // 查询刚刚产生的下一步待办任务，把节点名称回显给发起人（岗位任务的
        // assignee 为空，显示候选组岗位编码；这里只是展示，不参与流转决策）
        Task task = taskService.createTaskQuery()
                .processInstanceId(instanceId)
                .singleResult();

        String nextStep = "流程已结束";
        if(task != null){
            if (task.getAssignee() != null) {
                nextStep = task.getName() + "，办理人：" + task.getAssignee();
            } else {
                // 未认领的岗位待办：显示候选组
                List<org.flowable.identitylink.api.IdentityLink> links =
                        taskService.getIdentityLinksForTask(task.getId());
                List<String> groups = new ArrayList<>();
                for (org.flowable.identitylink.api.IdentityLink l : links) {
                    if ("candidate".equals(l.getType()) && l.getGroupId() != null) {
                        groups.add(l.getGroupId());
                    }
                }
                nextStep = task.getName() + "，等待岗位认领：" + (groups.isEmpty() ? "（未绑定岗位，请检查流程定义）" : String.join("、", groups));
            }
        }

        String msg = "申请提交成功！下一节点：" + nextStep + "。可在「代办任务」或「已办任务」中跟踪进度。";
        redirectAttributes.addFlashAttribute("msg",msg);
        return "redirect:/leaveApply/toPage";
    }
}
