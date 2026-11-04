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

    // 3. 保存当前 XML 到本地原始文件（源码目录 + 编译输出目录各写一份）
    @ResponseBody
    @PostMapping("/saveLocal")
    public String saveLocal(@RequestBody SaveLocalDTO dto){
        // 文件名安全校验：只允许字母数字下划线中文和点横线，且必须落在 processes 目录内
        String fileName = dto.getFileName();
        if (fileName == null || fileName.isBlank()) {
            return "保存失败：文件名不能为空";
        }
        fileName = fileName.replace("\\", "/").substring(fileName.replace("\\", "/").lastIndexOf('/') + 1);
        if (!fileName.matches("[\\w\\u4e00-\\u9fa5.\\-]+") || fileName.contains("..")) {
            return "保存失败：文件名含非法字符";
        }
        if (!fileName.endsWith(".bpmn") && !fileName.endsWith(".bpmn20.xml")) {
            fileName = fileName + ".bpmn20.xml";
        }

        String projectRoot = System.getProperty("user.dir");
        // 目标目录：源码目录优先（IDE 里改了能被 Git 跟踪），其次编译输出目录
        java.nio.file.Path srcDir = java.nio.file.Paths.get(projectRoot, "src", "main", "resources", "processes");
        java.nio.file.Path targetDir = java.nio.file.Paths.get(projectRoot, "target", "classes", "processes");

        try {
            java.nio.file.Files.createDirectories(srcDir);
            java.nio.file.Files.createDirectories(targetDir);
            byte[] bytes = dto.getBpmnXml().getBytes(StandardCharsets.UTF_8);
            java.nio.file.Files.write(srcDir.resolve(fileName), bytes);
            // target 同步一份，保证不重新构建时重启也能自动部署到新内容
            java.nio.file.Files.write(targetDir.resolve(fileName), bytes);
            log.info("流程XML已保存到本地: {}", srcDir.resolve(fileName));
            return "保存成功：" + fileName + "（已同步写入 src/main/resources/processes 与 target/classes/processes）";
        } catch (IOException e) {
            log.error("保存流程XML失败: {}", fileName, e);
            return "保存失败：" + e.getMessage();
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
    public static class SaveLocalDTO{
        private String fileName;
        private String bpmnXml;
    }

    @Data
    public static class DeployVO{
        private String deploymentId;
        private String name;
        private Object deployTime;
    }
}