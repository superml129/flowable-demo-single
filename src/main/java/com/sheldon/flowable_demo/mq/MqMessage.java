package com.sheldon.flowable_demo.mq;

import lombok.Data;

@Data
public class MqMessage {
    /** TASK_CREATED / TASK_REMIND */
    private String eventType;
    private String taskId;           // ★ 催办靠它反查任务是否还 pending
    private String procInstId;
    private String taskName;
    private String procDefName;
    private String taskDefKey;
    private String receiver;
    private String msgContent;
    private String assignee;         // 催办收件人
    private String bizKey;
    private String time;
    private String msgId;            // 发送消息的唯一标识
}

