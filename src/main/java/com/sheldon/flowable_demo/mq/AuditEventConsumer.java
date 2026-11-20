package com.sheldon.flowable_demo.mq;

import com.sheldon.flowable_demo.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@RocketMQMessageListener(topic = FlowEventProducer.AUDIT_TOPIC,
        consumerGroup = "flow-audit-consumer-group")
public class AuditEventConsumer implements RocketMQListener<MqMessage> {
    @Resource
    private AuditLogService auditLogService;

    @Override
    public void onMessage(MqMessage msg) {
        try {
            auditLogService.save(msg);   // 内部 catch DuplicateKeyException 视为成功
        } catch (Exception e) {
            log.error("审计落库失败 {}", msg, e);   // 吞异常防无限重试，MQ 持久化本身已是备份
        }
    }
}

