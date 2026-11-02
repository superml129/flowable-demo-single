package com.sheldon.flowable_demo.listener;

import com.sheldon.flowable_demo.entity.FlowMsg;
import com.sheldon.flowable_demo.mapper.FlowMsgMapper;
import com.sheldon.flowable_demo.util.SseMsgManager;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class NodeCompleteNotifyStarterListener implements ExecutionListener {

    @Resource
    private FlowMsgMapper flowMsgMapper;
    @Resource
    private SseMsgManager sseMsgManager;
    @Resource
    private RuntimeService runtimeService; // 直接注入RuntimeService，不再从execution拿
    @Resource
    private RepositoryService repositoryService; // 新增：用来读取bpmn模型

    @Override
    public void notify(DelegateExecution execution) {
        String activityId = execution.getCurrentActivityId();
        String procInsId = execution.getProcessInstanceId();

        // 跳过开始、结束节点，根据你的bpmn节点id适配
        if("startEvent".equals(activityId) || activityId.contains("endEvent")){
            return;
        }
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInsId)
                .singleResult();
        if(instance == null){
            return;
        }
        // 流程发起人
        String starter = instance.getStartUserId();
        if(starter == null || starter.isBlank()){
            log.warn("流程{}无发起人，跳过消息",procInsId);
            return;
        }

        // ========== 重点：根据activityId获取节点名称 ==========
        String procDefId = instance.getProcessDefinitionId();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(procDefId);
        FlowElement flowElement = bpmnModel.getMainProcess().getFlowElement(activityId);
        String taskName = activityId; // 默认兜底为id
        if(flowElement != null && flowElement.getName() != null){
            taskName = flowElement.getName();
        }
        String procName = instance.getName();

        // 入库消息
        FlowMsg msg = new FlowMsg();
        msg.setReceiver(starter);
        msg.setProcessInstanceId(procInsId);
        msg.setProcessDefName(procName);
        msg.setTaskDefKey(activityId);
        msg.setTaskName(taskName);
        msg.setMsgContent("【"+procName+"】节点【"+taskName+"】已审批完成");
        msg.setIsRead(0);
        flowMsgMapper.insert(msg);

        // SSE实时推送
        Map<String,Object> pushData = new HashMap<>();
        pushData.put("unReadCount", flowMsgMapper.countUnRead(starter));
        pushData.put("msg",msg);
        sseMsgManager.sendMsg(starter,pushData);
        log.info("推送消息给发起人:{} ,流程:{}",starter,procInsId);
    }
}