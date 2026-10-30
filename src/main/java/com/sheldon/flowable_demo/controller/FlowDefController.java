package com.sheldon.flowable_demo.controller;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/flowDef")
public class FlowDefController {

    @Resource
    private RepositoryService repositoryService;

    // 页面跳转：流程定义列表页面
    @GetMapping("/listPage")
    public String listPage(){
        return "flow/flowDefList";
    }

    // 接口：获取所有已定义流程
    @GetMapping("/list")
    @ResponseBody
    public List<ProcessDefVO> getProcessDefList(){
        List<ProcessDefinition> defList = repositoryService.createProcessDefinitionQuery()
                .latestVersion() // 只查最新版本
                .orderByProcessDefinitionKey()
                .asc()
                .list();

        return defList.stream().map(def -> {
            ProcessDefVO vo = new ProcessDefVO();
            vo.setId(def.getId());
            vo.setKey(def.getKey());
            vo.setName(def.getName());
            vo.setVersion(def.getVersion());
            vo.setDeploymentId(def.getDeploymentId());
            vo.setResourceName(def.getResourceName());
            return vo;
        }).collect(Collectors.toList());
    }

    @GetMapping("/view")
    public String viewDefDiagram(@RequestParam String processDefinitionId, Model model){
        model.addAttribute("processDefinitionId", processDefinitionId);
        return "flow/flowDefDiagram";
    }

    @GetMapping("/getDefXml")
    @ResponseBody
    public String getDefXml(@RequestParam String processDefinitionId) throws IOException {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if(pd == null){
            return "";
        }
        InputStream inputStream = repositoryService.getProcessModel(processDefinitionId);
        return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
    }

    @lombok.Data
    public static class ProcessDefVO{
        private String id;
        private String key;
        private String name;
        private Integer version;
        private String deploymentId;
        private String resourceName;
    }
}
