package com.sheldon.flowable_demo.mq;

import com.sheldon.flowable_demo.config.RemindProperties;
import com.sheldon.flowable_demo.entity.FlowMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * 流程事件生产者。
 * 监听器里只负责组装 FlowMsg 并发送到这里，写库/SSE 推送全部由 FlowEventConsumer 消费端完成，
 * 实现「流程引擎 ↔ 通知渠道」解耦：MQ 挂了只丢通知，不影响审批主流程。
 *
 * 注意：调用方（各 Listener）必须自己 try-catch，发送异常不允许向上抛进流程事务。
 */
@Slf4j
@Component
public class FlowEventProducer {

    /** 与 FlowEventConsumer 的 @RocketMQMessageListener(topic=...) 必须一致 */
    public static final String TOPIC = "flow-event-topic";

    /** 催办专用 topic（与 flow-event-topic 分开，消费者职责单一） */
    public static final String REMIND_TOPIC = "flow-remind-topic";

    /** 流程岗位变更事件 topic（与 flow-event-topic 分开，消费者职责单一） */
    public static final String POSITION_TOPIC = "flow-position-topic";

    /** 审计事件 topic（与 flow-event-topic 分开，消费者职责单一） */
    public static final String AUDIT_TOPIC = "flow-audit-topic";

    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private RemindProperties remindProperties;

    /** 审计事件：发送失败只记日志，绝不影响主流程（审计允许丢，业务不允许断） */
    public void sendAudit(String eventType, String operator, String detail) {
        try {
            MqMessage e = new MqMessage();
            e.setEventType(eventType);
            e.setBizKey(operator);
            e.setMsgContent(detail);
            e.setTime(String.valueOf(System.currentTimeMillis()));
            e.setMsgId(UUID.randomUUID().toString());
            rocketMQTemplate.syncSend(AUDIT_TOPIC, e);
        } catch (Exception ex) {
            log.warn("审计事件发送失败（不阻塞业务）type={} operator={}", eventType, operator, ex);
        }
    }

    /**
     * 发送岗位/用户变更事件（多实例间广播，各实例的 PositionChangeConsumer 消费）。
     *
     * changeType 取值与消费端约定：
     *   USER_CHANGE     bizKey=userId      用户信息/岗位归属变化，踢该用户的会话
     *   MEMBER_CHANGE   bizKey=userId      岗位成员增删，踢该用户的会话
     *   POSITION_CHANGE bizKey=positionId  岗位本身变化（停用/改名等），踢该岗位全部成员
     *
     * 发送失败只记日志不抛出：岗位变更广播丢了可以手动 /position/sync 补偿，
     * 不应打断管理页的保存操作。
     */
    public void sendPositionChanged(Integer userId, String changeType) {
        sendPositionChanged(changeType, String.valueOf(userId));
    }

    public void sendPositionChanged(String changeType, String bizKey) {
        try {
            MqMessage e = new MqMessage();
            e.setEventType(changeType);
            e.setBizKey(bizKey);
            e.setTime(String.valueOf(System.currentTimeMillis()));
            rocketMQTemplate.syncSend(POSITION_TOPIC, e);
            log.info("岗位变更广播已发送 type={} key={}", changeType, bizKey);
        } catch (Exception ex) {
            log.error("岗位变更广播发送失败 type={} key={}（可在岗位管理页手动「同步到引擎」补偿）",
                    changeType, bizKey, ex);
        }
    }

    /**
     * 发送延迟催办消息：不立即投递，到 deliverTime 才进入 flow-remind-topic。
     * 5.x 定时轮支持任意时长（4.x 的 delayLevel 最多只能 2 小时）。
     */
    public void sendTaskRemind(MqMessage event) {
        event.setEventType("TASK_REMIND");
        rocketMQTemplate.syncSendDeliverTimeMills(
                REMIND_TOPIC, event, System.currentTimeMillis() + remindProperties.getDelay().toMillis());
    }

    /**
     * 发送流程通知事件（任务完成、节点推进等）。
     * 用 syncSend（同步确认 broker 收到）而不是 oneway：通知可靠性优先，
     * 3 秒超时由 application.yaml 的 send-message-timeout 控制，
     * 超时/失败抛异常，由调用方的 try-catch 兜底，不会中断流程。
     */
    public void sendTaskCreatedEvent(MqMessage event) {
        event.setEventType("TASK_CREATED");
        rocketMQTemplate.syncSend(TOPIC, event);
    }
}
