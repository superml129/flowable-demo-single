package com.sheldon.flowable_demo.entity;

import lombok.Data;

/**
 * 附件元信息（sys_attachment 表）。
 * PDF 原文件存磁盘（文件名 = id.pdf），本表只记录元数据；
 * biz_id 关联业务：请假场景下为流程实例 ID。
 */
@Data
public class Attachment {
    /** 主键，32 位 UUID，同时也是磁盘文件名前缀（id.pdf） */
    public String id;
    /** 业务类型，如 LEAVE（请假） */
    public String bizType;
    /** 业务单据 id，请假场景 = 流程实例 ID（上传时还没有，提交后回填） */
    public String bizId;
    /** 原文件名（用户看到的名字） */
    public String originalName;
    /** 磁盘存储名（UUID.pdf，与原文件名脱钩，防路径穿越） */
    public String storedName;
    /** 文件大小（字节） */
    public Long sizeBytes;
    /** 上传人（登录账号） */
    public String uploader;
    public String createTime;
}
