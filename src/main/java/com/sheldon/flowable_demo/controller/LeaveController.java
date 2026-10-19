package com.sheldon.flowable_demo.controller;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class LeaveController {

    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Resource
    private HistoryService historyService;

    /**
     * 启动请假流程
     */
    @GetMapping("/start")
    public String start(){
        Map<String,Object> vars=new HashMap<>();
        vars.put("manager","zhangsan");
        var instance = runtimeService.startProcessInstanceByKey("leaveProcess", vars);
        return "流程实例id:"+instance.getId();
    }

    @GetMapping("/startGateway")
    public String startGateway(Integer dayCount){
        Map<String,Object> vars=new HashMap<>();
        vars.put("dayCount", dayCount);
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("leaveGatewayProcess", vars);
        return "排他网关流程实例id:"+instance.getId();
    }

    @GetMapping("/startParallel")
    public String startParallel(){
        Map<String,Object> vars=new HashMap<>();
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("leaveParallelProcess",vars);
        return "并行网关流程实例id:"+instance.getId();
    }

    /**
     * 查询zhangsan待办任务
     */
    @GetMapping("/queryTask")
    public List<Map<String,Object>> query(){
        List<Task> taskList = taskService.createTaskQuery()
                .taskAssignee("zhangsan")
                .list();

        List<Map<String,Object>> resultList = new ArrayList<>();
        for (Task task : taskList) {
            Map<String,Object> item = new HashMap<>();
            item.put("taskId", task.getId());
            item.put("taskName", task.getName());
            item.put("processInstanceId", task.getProcessInstanceId());
            item.put("assignee", task.getAssignee());
            item.put("createTime", task.getCreateTime());
            item.put("taskDefinitionKey", task.getTaskDefinitionKey());
            resultList.add(item);
        }
        return resultList;
    }

    @GetMapping("/queryUserTask")
    public List<Map<String,Object>> queryUserTask(String username){
        List<Task> taskList = taskService.createTaskQuery()
                .taskAssignee(username)
                .list();

        List<Map<String,Object>> resultList = new ArrayList<>();
        for (Task task : taskList) {
            Map<String,Object> item = new HashMap<>();
            item.put("taskId", task.getId());
            item.put("taskName", task.getName());
            item.put("processInstanceId", task.getProcessInstanceId());
            item.put("assignee", task.getAssignee());
            item.put("createTime", task.getCreateTime());
            item.put("taskDefinitionKey", task.getTaskDefinitionKey());
            resultList.add(item);
        }
        return resultList;
    }

    /**
     * 完成审批任务，传入taskId
     */
    @GetMapping("/complete")
    public String complete(String taskId){
        taskService.complete(taskId);
        return "任务完成";
    }

    /**
     * 完成代办任务
     */
    @PostMapping("/task/complete")
    @ResponseBody
    public String completeTask(@RequestParam String taskId){
        Map<String,Object> vars = new HashMap<>();
        taskService.complete(taskId,vars);
        return "success";
    }
}