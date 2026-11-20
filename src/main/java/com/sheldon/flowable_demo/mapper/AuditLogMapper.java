package com.sheldon.flowable_demo.mapper;

import com.sheldon.flowable_demo.entity.AuditLog;
import com.sheldon.flowable_demo.entity.FlowMsg;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

public interface AuditLogMapper {
    @Insert("INSERT INTO sys_audit_log (msg_id, event_type, operator, detail, create_time) " +
            "VALUES (#{msgId}, #{eventType}, #{operator}, #{detail}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(AuditLog auditLog);
}
