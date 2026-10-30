package com.sheldon.flowable_demo.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/flowDiagram")
public class FlowInstanceDiagramController {

    @Resource
    private RuntimeService runtimeService;
    @Resource
    private HistoryService historyService;
    @Resource
    private RepositoryService repositoryService;

    /**
     * 获取实例高亮信息 + bpmn xml
     */
    @ResponseBody
    @GetMapping("/getInstanceInfo")
    public DiagramVO getInstanceInfo(@RequestParam String processInstanceId){
        DiagramVO vo = new DiagramVO();

        // 1. 查询流程定义ID
        String procDefId = null;
        // 运行中的实例
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (pi != null) {
            procDefId = pi.getProcessDefinitionId();
        } else {
            // 已经结束，查历史流程实例
            HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (hpi != null) {
                procDefId = hpi.getProcessDefinitionId();
            }
        }
        if(procDefId == null){
            vo.setBpmnXml("");
            return vo;
        }

        // 2. 读取bpmn xml
        ProcessDefinition pd = repositoryService.getProcessDefinition(procDefId);
        String resourceName = pd.getResourceName();
        String deploymentId = pd.getDeploymentId();
        // 用 getResourceAsStream 获取流，转byte数组
        try (InputStream inputStream = repositoryService.getResourceAsStream(deploymentId, resourceName)) {
            byte[] bytes = inputStream.readAllBytes();
            vo.setBpmnXml(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("读取流程资源失败", e);
            vo.setBpmnXml("");
        }

        // 3. 【已完成节点】历史活动，结束时间不为空
        List<HistoricActivityInstance> allHistActivity = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        List<String> finishedIds = new ArrayList<>();
        List<String> activeIds = new ArrayList<>();
        for(HistoricActivityInstance ha : allHistActivity){
            if(ha.getEndTime() != null){
                finishedIds.add(ha.getActivityId());
            }else{
                activeIds.add(ha.getActivityId());
            }
        }
        vo.setFinishedNodeIds(finishedIds);
        vo.setActiveNodeIds(activeIds);
        return vo;
    }

    @Data
    public static class DiagramVO{
        private String bpmnXml;
        private List<String> finishedNodeIds; // 已完成
        private List<String> activeNodeIds;   // 当前待办/运行中
    }

    // 页面入口
    @RequestMapping("/view")
    public String diagramPage(){
        return "flow/flowDiagramView";
    }
}