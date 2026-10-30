package com.sheldon.flowable_demo.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentQuery;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/flow")
public class FlowDesignController {

    @Resource
    private RepositoryService repositoryService;

    @ResponseBody
    @PostMapping("/deploy")
    public String deploy(@RequestBody DeployDTO dto){
        try {
            Deployment deploy = repositoryService.createDeployment()
                    .name(dto.getName())
                    .addBytes(dto.getName()+".bpmn20.xml", dto.getBpmnXml().getBytes(StandardCharsets.UTF_8))
                    .deploy();
            log.info("流程部署成功，deploymentId:{}", deploy.getId());
            return "部署成功，部署ID:"+deploy.getId();
        }catch (Exception e){
            log.error("部署异常",e);
            return "部署失败："+e.getMessage();
        }
    }

    // 1. 查询所有部署列表（给前端下拉选择）
    @ResponseBody
    @GetMapping("/listDeploy")
    public List<DeployVO> listDeploy(){
        List<Deployment> deployments = repositoryService.createDeploymentQuery()
                .orderByDeploymentTime()
                .desc()
                .list();
        List<DeployVO> voList = deployments.stream().map(d -> {
            DeployVO vo = new DeployVO();
            vo.setDeploymentId(d.getId());
            vo.setName(d.getName());
            vo.setDeployTime(d.getDeploymentTime());
            return vo;
        }).collect(Collectors.toList());
        return voList;
    }

    // 2. 根据部署ID读取bpmn xml资源
    @ResponseBody
    @GetMapping("/getXml")
    public String getXml(@RequestParam String deploymentId){
        // 拿到该部署下全部资源
        List<String> resourceNames = repositoryService.getDeploymentResourceNames(deploymentId);
        String bpmnResourceName = null;
        // 找 .bpmn20.xml / .bpmn 文件
        for(String name : resourceNames){
            if(name.endsWith(".bpmn20.xml") || name.endsWith(".bpmn")){
                bpmnResourceName = name;
                break;
            }
        }
        if(bpmnResourceName == null){
            return null;
        }
        // 用流读取，不再用Resource
        try (InputStream inputStream = repositoryService.getResourceAsStream(deploymentId, bpmnResourceName)) {
            if (inputStream == null) {
                return null;
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取流程xml失败", e);
            return null;
        }
    }

    @RequestMapping("/editor")
    public ModelAndView editorPage(){
        return new ModelAndView("flow/bpmnEditor");
    }

    @Data
    public static class DeployDTO{
        private String name;
        private String bpmnXml;
    }

    @Data
    public static class DeployVO{
        private String deploymentId;
        private String name;
        private Object deployTime;
    }
}