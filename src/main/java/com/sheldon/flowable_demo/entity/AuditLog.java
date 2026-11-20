package com.sheldon.flowable_demo.entity;

import lombok.Data;

@Data
public class AuditLog {
    private Long id;
    private String msgId;
    private String eventType;
    private String operator;
    private String detail;
    private String createTime;
}
