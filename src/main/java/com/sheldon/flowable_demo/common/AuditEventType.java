package com.sheldon.flowable_demo.common;

import lombok.Getter;

/**
 * 审计事件类型枚举
 */
@Getter
public enum AuditEventType {

    /**
     * 登录审计
     * bizKey: 操作者username
     * msgContent: 登录成功
     */
    AUDIT_LOGIN("AUDIT_LOGIN", "操作者username", "登录成功"),

    /**
     * 密码重置审计
     * bizKey: 目标userId
     * msgContent: 重置密码
     */
    AUDIT_PASSWORD_RESET("AUDIT_PASSWORD_RESET", "目标userId", "重置密码"),

    /**
     * 岗位调整审计
     * bizKey: 目标userId
     * msgContent: 调整岗位归属：[...]
     */
    AUDIT_POSITION_CHANGE("AUDIT_POSITION_CHANGE", "目标userId", "调整岗位归属：[...]"),

    /**
     * 流程部署审计
     * bizKey: 部署id/processId
     * msgContent: 部署流程 xxx
     */
    AUDIT_PROCESS_DEPLOY("AUDIT_PROCESS_DEPLOY", "部署id/processId", "部署流程 xxx");


    /**
     * 事件编码（投递MQ使用）
     */
    private final String eventType;
    /**
     * bizKey业务主键说明
     */
    private final String bizKeyDesc;
    /**
     * 默认消息内容（可动态替换）
     */
    private final String defaultMsgContent;

    AuditEventType(String eventType, String bizKeyDesc, String defaultMsgContent) {
        this.eventType = eventType;
        this.bizKeyDesc = bizKeyDesc;
        this.defaultMsgContent = defaultMsgContent;
    }

}
