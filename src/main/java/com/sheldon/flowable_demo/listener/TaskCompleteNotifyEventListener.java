package com.sheldon.flowable_demo.listener;

import com.sheldon.flowable_demo.entity.FlowMsg;
import com.sheldon.flowable_demo.mapper.FlowMsgMapper;
import com.sheldon.flowable_demo.util.SseMsgManager;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局任务完成通知监听器。
 * 监听引擎的 TASK_COMPLETED 事件（任何流程、任何用户任务节点完成都会触发），
 * 通过 FlowableEngineConfig 注册到引擎，无需在 BPMN 里逐节点配置 executionListener。
 *
 * 注意：本 Bean 在引擎创建过程中被引用（EngineConfigurationConfigurer），
 * 因此 RuntimeService 必须用 ObjectProvider 延迟获取，
 * 否则会形成 processEngine -> 本Bean -> runtimeServiceBean -> processEngine 的循环依赖。
 */
@Slf4j
@Component
public class TaskCompleteNotifyEventListener extends AbstractFlowableEngineEventListener {

    @Resource
    private FlowMsgMapper flowMsgMapper;
    @Resource
    private SseMsgManager sseMsgManager;
    // 延迟获取引擎服务，避免循环依赖
    private final ObjectProvider<RuntimeService> runtimeServiceProvider;
    private final ObjectProvider<RepositoryService> repositoryServiceProvider;

    public TaskCompleteNotifyEventListener(ObjectProvider<RuntimeService> runtimeServiceProvider,
                                           ObjectProvider<RepositoryService> repositoryServiceProvider) {
        this.runtimeServiceProvider = runtimeServiceProvider;
        this.repositoryServiceProvider = repositoryServiceProvider;
    }

    @Override
    protected void taskCompleted(FlowableEngineEntityEvent event) {
        // 只处理用户任务完成事件
        if (!(event.getEntity() instanceof TaskEntity)) {
            return;
        }
        TaskEntity task = (TaskEntity) event.getEntity();
        String procInsId = task.getProcessInstanceId();
        try {
            RuntimeService runtimeService = runtimeServiceProvider.getObject();
            ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(procInsId)
                    .singleResult();
            if (instance == null) {
                return;
            }
            // 流程发起人
            String starter = instance.getStartUserId();
            if (starter == null || starter.isBlank()) {
                // 兜底：未设置认证用户的老数据，尝试读取业务变量 applyUser
                Object applyUser = runtimeService.getVariable(procInsId, "applyUser");
                if (applyUser != null && !applyUser.toString().isBlank()) {
                    starter = applyUser.toString();
                } else {
                    log.warn("流程{}无发起人，跳过消息", procInsId);
                    return;
                }
            }
            String taskName = task.getName() != null ? task.getName() : task.getTaskDefinitionKey();
            // 取流程定义名称（流程实例的 name 通常未设置，不能用 instance.getName()）
            ProcessDefinition procDef = repositoryServiceProvider.getObject()
                    .createProcessDefinitionQuery()
                    .processDefinitionId(task.getProcessDefinitionId())
                    .singleResult();
            String procName = procDef != null && procDef.getName() != null
                    ? procDef.getName()
                    : (procDef != null ? procDef.getKey() : "流程");

            // 入库消息
            FlowMsg msg = new FlowMsg();
            msg.setReceiver(starter);
            msg.setProcessInstanceId(procInsId);
            msg.setProcessDefName(procName);
            msg.setTaskDefKey(task.getTaskDefinitionKey());
            msg.setTaskName(taskName);
            msg.setMsgContent("【" + procName + "】节点【" + taskName + "】已审批完成");
            msg.setIsRead(0);
            flowMsgMapper.insert(msg);

            // SSE实时推送
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("unReadCount", flowMsgMapper.countUnRead(starter));
            pushData.put("msg", msg);
            sseMsgManager.sendMsg(starter, pushData);
            log.info("推送消息给发起人:{} ,流程:{}", starter, procInsId);
        } catch (Exception e) {
            // 通知失败不能影响主流程事务
            log.error("任务完成通知失败, procInsId=" + procInsId, e);
        }
    }
}
