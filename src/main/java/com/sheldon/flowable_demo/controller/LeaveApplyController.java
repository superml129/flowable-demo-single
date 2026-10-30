package com.sheldon.flowable_demo.controller;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.annotation.Resource;
import java.util.HashMap;
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
    public String submitLeave(@RequestParam String applyUser,
                              @RequestParam Integer leaveDays,
                              @RequestParam String leaveReason,
                              @RequestParam String manager,   //新增
                              RedirectAttributes redirectAttributes){

        Map<String,Object> variables = new HashMap<>();
        variables.put("applyUser",applyUser);
        variables.put("leaveDays",leaveDays);
        variables.put("leaveReason",leaveReason);
        variables.put("manager",manager);

        // 启动流程，key对应bpmn里面的id
        String instanceId = runtimeService.startProcessInstanceByKey("leaveProcess", variables).getId();

        // 查询刚刚产生的下一步待办任务
        Task task = taskService.createTaskQuery()
                .processInstanceId(instanceId)
                .singleResult();

        String nextAssignee = "";
        if(task != null){
            nextAssignee = task.getAssignee();
        }

        String msg = "请假申请提交成功！流程实例ID："+instanceId + "，下一步处理人："+ nextAssignee;
        redirectAttributes.addFlashAttribute("msg",msg);
        return "redirect:/leaveApply/toPage";
    }
}
