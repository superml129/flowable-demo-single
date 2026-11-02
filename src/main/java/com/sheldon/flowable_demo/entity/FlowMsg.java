package com.sheldon.flowable_demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FlowMsg {
    private Long id;
    private String receiver;
    private String processInstanceId;
    private String processDefName;
    private String taskDefKey;
    private String taskName;
    private String msgContent;
    private Integer isRead;
    private LocalDateTime createTime;
}