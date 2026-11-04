package com.sheldon.flowable_demo.controller;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/todoTask")
public class PageController {

    @Resource
    private TaskService taskService;
    @Resource
    private HistoryService historyService;

    @GetMapping("/toPage")
    public ModelAndView todoTaskList(Model model,
                                     HttpSession session,
                                     HttpServletRequest request,
                                     @RequestParam(required = false) String assignee,
                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "10") Integer pageSize,
                                     @RequestParam(defaultValue = "createTime") String sortField,
                                     @RequestParam(defaultValue = "desc") String sortOrder) {

        // =====从session读取登录用户（适配你当前拦截器方案）=====
        String loginUser = (String) session.getAttribute("loginUser");
        log.info("【代办查询】当前登录用户：{}", loginUser);
        // 最终用来查询的办理人
        String finalAssignee;

        if("admin".equals(loginUser)){
            // admin：使用页面传入的assignee；assignee为空时，查询全部任务
            finalAssignee = assignee;
        }else{
            // 普通用户：强制只能查询自己，忽略前端传入的assignee
            finalAssignee = loginUser;
        }
        model.addAttribute("loginUser", loginUser);

        // 分页起始下标：pageNum从1开始，firstResult=(pageNum-1)*pageSize
        int firstResult = (pageNum - 1) * pageSize;

        // 1.查询代办总数
        TaskQuery countQuery = taskService.createTaskQuery();
        if (finalAssignee != null && finalAssignee.trim().length() > 0) {
            // 关键：用 taskCandidateOrAssigned 而不是 taskAssignee
            // 候选组任务（candidateGroups）在认领前 assignee 为空，
            // 只查 taskAssignee 会漏掉"岗位待办"，组内成员看不到分给岗位的任务
            countQuery.taskCandidateOrAssigned(finalAssignee.trim());
        }
        long total = countQuery.count();

        TaskQuery taskQuery = taskService.createTaskQuery();
        if(finalAssignee != null && finalAssignee.trim().length()>0){
            taskQuery.taskCandidateOrAssigned(finalAssignee.trim());
        }

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
        } else if ("assignee".equals(sortField)) {
            if ("asc".equals(sortOrder)) {
                taskQuery.orderByTaskAssignee().asc();
            } else {
                taskQuery.orderByTaskAssignee().desc();
            }
        }

        // 2.分页查询代办任务
        List<Task> taskList = taskQuery
                .listPage(firstResult, pageSize);

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Task t : taskList) {
            Map<String, Object> map = new HashMap<>();
            map.put("taskId", t.getId());
            map.put("taskName", t.getName());
            map.put("processInstanceId", t.getProcessInstanceId());
            map.put("assignee", t.getAssignee());
            // assignee 为空 = 候选组任务（岗位待办），需要先认领
            map.put("unclaimed", t.getAssignee() == null || t.getAssignee().isBlank());
            map.put("createTime", t.getCreateTime());
            resultList.add(map);
        }
        model.addAttribute("todoList", resultList);
        model.addAttribute("pageNum", pageNum);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("total", total);
        model.addAttribute("totalPages", (total + pageSize - 1) / pageSize); //总页数向上取整
        model.addAttribute("searchUser", assignee);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);

        // 核心判断：异步AJAX返回表格片段
        String ajaxHeader = request.getHeader("X-Requested-With");
        ModelAndView mv;
        if ("XMLHttpRequest".equals(ajaxHeader)) {
            mv = new ModelAndView("fragments/todoTaskTable");
        } else {
            mv = new ModelAndView("todoTask");
        }
        return mv;
    }

    @GetMapping("/donePage")
    public ModelAndView doneTaskList(Model model,
                                     HttpSession session,
                                     HttpServletRequest request,
                                     @RequestParam(required = false) String assignee,
                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "10") Integer pageSize,
                                     @RequestParam(defaultValue = "createTime") String sortField,
                                     @RequestParam(defaultValue = "desc") String sortOrder) {
        // =====从session读取登录用户（适配你当前拦截器方案）=====
        String loginUser = (String) session.getAttribute("loginUser");
        if (loginUser == null) {
            loginUser = "匿名用户";
        }
        model.addAttribute("loginUser", loginUser);
        log.info("【已办查询】当前登录用户：{}", loginUser);
        // 最终用来查询的办理人
        String finalAssignee;

        if("admin".equals(loginUser)){
            // admin：使用页面传入的assignee；assignee为空时，查询全部任务
            finalAssignee = assignee;
        }else{
            // 普通用户：强制只能查询自己，忽略前端传入的assignee
            finalAssignee = loginUser;
        }

        // 分页起始下标：pageNum从1开始，firstResult=(pageNum-1)*pageSize
        int firstResult = (pageNum - 1) * pageSize;

        // 1.查询当前用户已办总数
        HistoricTaskInstanceQuery countQuery = historyService.createHistoricTaskInstanceQuery();
        if (finalAssignee != null && finalAssignee.trim().length() > 0) {
            countQuery.taskAssignee(finalAssignee.trim());
        }
        countQuery.finished();
        long total = countQuery.count();

        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery();
        if (finalAssignee != null && finalAssignee.trim().length() > 0) {
            taskQuery.taskAssignee(finalAssignee.trim());
        }
        taskQuery.finished();
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
        } else if ("assignee".equals(sortField)) {
            if ("asc".equals(sortOrder)) {
                taskQuery.orderByTaskAssignee().asc();
            } else {
                taskQuery.orderByTaskAssignee().desc();
            }
        }

        List<HistoricTaskInstance> historicList = taskQuery
                .listPage(firstResult, pageSize);

        List<Map<String, Object>> doneList = new ArrayList<>();
        for (HistoricTaskInstance ht : historicList) {
            Map<String, Object> map = new HashMap<>();
            map.put("taskId", ht.getId());
            map.put("taskName", ht.getName());
            map.put("processInstanceId", ht.getProcessInstanceId());
            map.put("assignee", ht.getAssignee());
            map.put("createTime", ht.getCreateTime());
            map.put("endTime", ht.getEndTime());
            doneList.add(map);
        }
        model.addAttribute("doneList", doneList);
        model.addAttribute("pageNum", pageNum);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("total", total);
        model.addAttribute("totalPages", (total + pageSize - 1) / pageSize);
        model.addAttribute("searchUser", assignee);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);

        String ajaxHeader = request.getHeader("X-Requested-With");
        ModelAndView mv;
        if ("XMLHttpRequest".equals(ajaxHeader)) {
            mv = new ModelAndView("fragments/doneTaskTable");
        } else {
            mv = new ModelAndView("doneTask");
        }
        return mv;
    }

    //=======新增：任务办理表单页面=======
    @GetMapping("/toAudit")
    public ModelAndView toAuditPage(@RequestParam String taskId,
                                    Model model,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            redirectAttributes.addFlashAttribute("msg", "该任务不存在或已被他人处理！");
            return new ModelAndView("redirect:/todoTask/toPage");
        }
        // 候选组任务：进入办理页时自动签收给当前登录用户
        // 签收后如果又被别人抢先，claim 会抛异常，这里捕获后提示
        String loginUser = (String) session.getAttribute("loginUser");
        if (task.getAssignee() == null && loginUser != null) {
            try {
                taskService.claim(taskId, loginUser);
                task = taskService.createTaskQuery().taskId(taskId).singleResult();
            } catch (Exception e) {
                log.warn("任务签收失败，可能已被他人认领，taskId:{}", taskId, e);
                redirectAttributes.addFlashAttribute("msg", "该任务已被其他人领取，请刷新待办列表");
                return new ModelAndView("redirect:/todoTask/toPage");
            }
        }
        model.addAttribute("task", task);
        return new ModelAndView("auditTask");
    }

    //提交审批（通过/驳回）
    @PostMapping("/doAudit")
    public String doAudit(@RequestParam String taskId,
                          @RequestParam String auditResult,
                          @RequestParam String auditComment,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        // 查询当前任务
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task == null) {
            redirectAttributes.addFlashAttribute("msg", "该任务不存在或已审批完成！");
            return "redirect:/todoTask/toPage";
        }
        // 兜底：未经办理页直接提交时，先把任务签收给当前用户，否则组任务无法 complete
        String loginUser = (String) session.getAttribute("loginUser");
        if (task.getAssignee() == null && loginUser != null) {
            try {
                taskService.claim(taskId, loginUser);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("msg", "该任务已被其他人领取！");
                return "redirect:/todoTask/toPage";
            }
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("auditResult", auditResult); // pass / reject
        vars.put("auditComment", auditComment);
        taskService.complete(taskId, vars);
        redirectAttributes.addFlashAttribute("msg", "审批提交成功！");
        return "redirect:/home/index";
    }

    //=======领取（签收）任务claim：默认签收给当前登录用户=======
    @GetMapping("/claimTask")
    public String claimTask(@RequestParam String taskId,
                            @RequestParam(required = false) String username,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        String loginUser = (String) session.getAttribute("loginUser");
        String target = (username != null && !username.isBlank()) ? username : loginUser;
        if (target == null) {
            redirectAttributes.addFlashAttribute("msg", "请先登录");
            return "redirect:/login/toPage";
        }
        try {
            taskService.claim(taskId, target);
            redirectAttributes.addFlashAttribute("msg", "任务领取成功");
        } catch (Exception e) {
            log.warn("任务领取失败, taskId:{}", taskId, e);
            redirectAttributes.addFlashAttribute("msg", "该任务已被其他人领取");
        }
        return "redirect:/home/index";
    }
}