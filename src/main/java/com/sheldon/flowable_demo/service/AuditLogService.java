package com.sheldon.flowable_demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.sheldon.flowable_demo.entity.AuditLog;
import com.sheldon.flowable_demo.mapper.AuditLogMapper;
import com.sheldon.flowable_demo.mq.MqMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
@DS("userdb")
public class AuditLogService {
    @Resource
    private AuditLogMapper auditLogMapper;

    /**
     * 保存审计日志
     * @param msg 审计消息
     */
    public void save(MqMessage msg) {
        AuditLog auditLog = new AuditLog();
        auditLog.setMsgId(msg.getMsgId());
        auditLog.setEventType(msg.getEventType());
        auditLog.setOperator(msg.getBizKey());
        auditLog.setDetail(msg.getMsgContent());
        auditLog.setCreateTime(msg.getTime());
        auditLogMapper.insert(auditLog);
    }
}
