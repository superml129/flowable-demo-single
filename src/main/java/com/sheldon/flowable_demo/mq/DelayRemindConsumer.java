package com.sheldon.flowable_demo.mq;

import com.sheldon.flowable_demo.config.RemindProperties;
import com.sheldon.flowable_demo.entity.FlowMsg;
import com.sheldon.flowable_demo.mapper.FlowMsgMapper;
import com.sheldon.flowable_demo.service.SysPositionService;
import com.sheldon.flowable_demo.util.SseMsgManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = FlowEventProducer.REMIND_TOPIC,
        consumerGroup = "flow-remind-consumer-group")
public class DelayRemindConsumer implements RocketMQListener<MqMessage> {

    @Resource
    private org.flowable.engine.TaskService taskService;
    @Resource
    private FlowMsgMapper flowMsgMapper;
    @Resource
    private SseMsgManager sseMsgManager;
    @Resource
    private SysPositionService sysPositionService;
    @Resource
    private RemindProperties remindProperties;

    @Override
    public void onMessage(MqMessage event) {
        if (event == null || event.getTaskId() == null) return;
        // ★ 核心：到点后先确认任务还在（未办结）。已审批完 → 直接丢弃
        org.flowable.task.api.Task task = taskService.createTaskQuery()
                .taskId(event.getTaskId())
                .singleResult();
        if (task == null) {
            log.info("任务已办结，取消催办: taskId={}", event.getTaskId());
            return;
        }
        Set<String> receivers = new LinkedHashSet<>();
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            receivers.add(task.getAssignee());
        } else {
            // 候选组任务：从 IdentityLink 反查候选人/候选组
            for (IdentityLink link : taskService.getIdentityLinksForTask(event.getTaskId())) {
                if (!IdentityLinkType.CANDIDATE.equals(link.getType())) continue;
                if (link.getUserId() != null) {
                    receivers.add(link.getUserId());          // 直接指名候选人
                } else if (link.getGroupId() != null) {
                    // groupId 就是岗位编码（如 POS_DEPT_MANAGER）→ 查岗位成员
                    // ★ 必须走 Service 层（@DS("userdb")），别直调 Mapper，否则又是 BadSqlGrammar
                    sysPositionService.findMemberUsernames(link.getGroupId())
                            .forEach(receivers::add);
                }
            }
        }
        // 组装催办消息，复用现有写库+SSE逻辑
        for (String receiver : receivers) {
            FlowMsg msg = new FlowMsg();
            msg.setReceiver(receiver);
            msg.setProcessInstanceId(event.getProcInstId());
            msg.setProcessDefName(event.getProcDefName());
            msg.setTaskDefKey(task.getTaskDefinitionKey());
            msg.setTaskName(event.getTaskName());
            msg.setMsgContent("【催办】您有任务【" + event.getTaskName() + "】已超过" + formatDelay() + "未处理");
            msg.setIsRead(0);
            flowMsgMapper.insert(msg);

            Map<String, Object> pushData = new HashMap<>();
            pushData.put("unReadCount", flowMsgMapper.countUnRead(receiver));
            pushData.put("msg", msg);
            sseMsgManager.sendMsg(receiver, pushData);
        }
    }
    /**
     * 格式化催办延迟时长，便于日志输出
     */
    private String formatDelay() {
        Duration d = remindProperties.getDelay();
        if (d.toHours() >= 1)  return d.toHours() + "小时";
        if (d.toMinutes() >= 1) return d.toMinutes() + "分钟";
        return d.getSeconds() + "秒";
    }
}

