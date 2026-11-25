package com.sheldon.flowable_demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 附件存储配置。
 * PDF 原文件存本地磁盘目录，数据库只存元信息（见 sys_attachment 表），
 * 目录做成可配置，避免路径写死在代码里。
 */
@Data
@Component
@ConfigurationProperties(prefix = "flow.attachment")
public class AttachmentProperties {
    /** 附件落盘目录，不存在时会在保存时自动创建 */
    private String dir = "./attachments";
}
