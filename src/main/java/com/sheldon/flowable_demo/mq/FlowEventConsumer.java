package com.sheldon.flowable_demo.mq;

import com.sheldon.flowable_demo.entity.FlowMsg;
import com.sheldon.flowable_demo.mapper.FlowMsgMapper;
import com.sheldon.flowable_demo.util.SseMsgManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 流程事件消费者：收到事件后写 flow_msg + SSE 实时推送。
 * 这两段逻辑从 TaskCompleteNotifyEventListener 等监听器中剥离而来，
 * 以后加邮件、企业微信等通知渠道，只改这里，流程引擎无感。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = FlowEventProducer.TOPIC,          // 必须与生产者一致
        consumerGroup = "flow-msg-consumer-group",
        messageModel = MessageModel.CLUSTERING)
public class FlowEventConsumer implements RocketMQListener<MqMessage> {

    @Resource
    private FlowMsgMapper flowMsgMapper;
    @Resource
    private SseMsgManager sseMsgManager;

    @Override
    public void onMessage(MqMessage mm) {
        // 基本校验：收件人是写库和推送的前提
        if (mm == null || mm.getReceiver() == null || mm.getReceiver().isBlank()) {
            log.warn("收到无效流程事件，跳过: {}", mm);
            return;
        }
        try {
            // 写 flow_msg（把监听器里原来的逻辑搬过来）
            FlowMsg msg = new FlowMsg();
            msg.setReceiver(mm.getReceiver());
            msg.setProcessInstanceId(mm.getProcInstId());
            msg.setProcessDefName(mm.getProcDefName());
            msg.setTaskDefKey(mm.getTaskDefKey());
            msg.setTaskName(mm.getTaskName());
            msg.setMsgContent("【" + mm.getProcDefName() + "】节点【" + mm.getTaskName() + "】已审批完成");
            msg.setIsRead(0);
            flowMsgMapper.insert(msg);
        } catch (DuplicateKeyException e) {
            // RocketMQ 是至少一次投递，重试会导致同一条消息到达两次；
            // 若给 flow_msg 建了 (receiver, task_def_key, msg_content) 之类唯一索引，
            // 重复消息会以唯一键冲突的形式落到这里，静默跳过即完成幂等。
            log.info("重复流程事件，已幂等跳过: receiver={}, taskName={}",
                    mm.getReceiver(), mm.getTaskName());
            return;
        }

        // SSE 实时推送（未读数 + 消息本体）
        Map<String, Object> pushData = new HashMap<>();
        pushData.put("unReadCount", flowMsgMapper.countUnRead(mm.getReceiver()));
        pushData.put("msg", mm);
        sseMsgManager.sendMsg(mm.getReceiver(), pushData);
        log.info("MQ消费: 推送消息给 {}，流程: {}", mm.getReceiver(), mm.getProcInstId());
    }
}
