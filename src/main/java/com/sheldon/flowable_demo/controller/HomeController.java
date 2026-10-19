package com.sheldon.flowable_demo.controller;

import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/home")
public class HomeController {

    @Resource
    private TaskService taskService;


    @GetMapping("/index")
    public String index(Model model, HttpSession session,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize,
                        @RequestParam(defaultValue = "createTime") String sortField,
                        @RequestParam(defaultValue = "desc") String sortOrder) {

        // 从SpringSecurity获取当前登录账号
        // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // String loginUser = auth.getName();

        //从Session获取登录用户，不再使用SecurityContext
        String loginUser = (String) session.getAttribute("loginUser");

        int firstResult = (pageNum -1)*pageSize;
        //查询当前用户代办总数
        TaskQuery taskQuery = taskService.createTaskQuery()
                .taskAssignee(loginUser);
        long todoTotal = taskQuery.count();

        // 动态排序
        if ("createTime".equals(sortField)) {
            if ("asc".equals(sortOrder)) {
                taskQuery.orderByTaskCreateTime().asc();
            } else {
                taskQuery.orderByTaskCreateTime().desc();
            }
        } else if ("taskName".equals(sortField)) {
            if ("asc".equals(sortOrder)) {
                taskQuery.orderByTaskName().asc();
            } else {
                taskQuery.orderByTaskName().desc();
            }
        }

        //首页分页展示当前登录用户的代办任务
        List<Task> taskList = taskQuery
                .listPage(firstResult,pageSize);

        List<Map<String,Object>> todoList = new ArrayList<>();
        for(Task t:taskList){
            Map<String,Object> item=new HashMap<>();
            item.put("taskId",t.getId());
            item.put("taskName",t.getName());
            item.put("processInstanceId",t.getProcessInstanceId());
            item.put("createTime",t.getCreateTime());
            todoList.add(item);
        }

        long totalPages = (todoTotal + pageSize -1)/pageSize;
        // 如果当前页大于总页数，跳回第一页（切换条数后防空白页）
        if(pageNum > totalPages && totalPages >0){
            pageNum = 1;
        }

        model.addAttribute("loginUser",loginUser);
        model.addAttribute("homeTodoList",todoList);
        model.addAttribute("pageNum",pageNum);
        model.addAttribute("pageSize",pageSize);
        model.addAttribute("todoTotal",todoTotal);
        model.addAttribute("totalPages", (todoTotal + pageSize -1)/pageSize);
        model.addAttribute("sortField",sortField);
        model.addAttribute("sortOrder",sortOrder);
        return "index";
    }
}