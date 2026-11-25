package com.sheldon.flowable_demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.sheldon.flowable_demo.config.AttachmentProperties;
import com.sheldon.flowable_demo.entity.Attachment;
import com.sheldon.flowable_demo.mapper.AttachmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * 附件服务：PDF 原文件存磁盘目录，元数据存 sys_attachment 表。
 *
 * 存储命名策略：磁盘文件名固定为「32位UUID + .pdf」，与用户上传的原始文件名
 * 完全脱钩 —— 原文件名（含中文、空格、潜在恶意后缀）只存在数据库里，
 * 从根上杜绝路径穿越与可执行文件上传问题（后缀写死 .pdf，服务器也不会执行）。
 */
@Slf4j
@Service
@DS("userdb")
public class AttachmentService {

    /** 允许的文件后缀 */
    public static final String PDF_SUFFIX = ".pdf";
    /** 单文件大小上限：10MB */
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    @Resource
    private AttachmentMapper attachmentMapper;
    @Resource
    private AttachmentProperties attachmentProperties;

    /**
     * 保存附件：写磁盘 + 落库，返回附件 id。
     *
     * @param originalName 用户上传的原始文件名
     * @param in           文件输入流（MultipartFile.getInputStream()）
     * @param size         文件大小（字节）
     * @param uploader     上传人登录账号
     * @throws IllegalArgumentException 格式或大小不符合要求（调用方转成页面提示）
     */
    public String save(String originalName, InputStream in, long size, String uploader) {
        // ① 服务端校验（页面的 JS 校验只能算体验优化，不能作为安全边界）
        if (originalName == null || !originalName.toLowerCase().endsWith(PDF_SUFFIX)) {
            throw new IllegalArgumentException("附件仅支持 PDF 格式");
        }
        if (size <= 0 || size > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("附件大小必须在 0 - 10MB 之间");
        }
        // ② 写磁盘：文件名用生成的 UUID，目录不存在则创建
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String storedName = id + PDF_SUFFIX;
        Path dir = Paths.get(attachmentProperties.getDir());
        Path target = dir.resolve(storedName);
        try {
            Files.createDirectories(dir);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("附件写磁盘失败 dir={} storedName={}", dir, storedName, e);
            throw new IllegalArgumentException("附件保存失败，请稍后重试");
        }
        // ③ 元数据落库（biz_id 此时还没有，提交后回填流程实例 ID）
        Attachment att = new Attachment();
        att.id = id;
        att.bizType = "LEAVE";
        att.originalName = sanitizeName(originalName);
        att.storedName = storedName;
        att.sizeBytes = size;
        att.uploader = uploader;
        try {
            attachmentMapper.insert(att);
        } catch (Exception e) {
            // 落库失败要把磁盘文件删掉，避免留孤儿文件
            try { Files.deleteIfExists(target); } catch (Exception ignore) { }
            log.error("附件元数据入库失败 id={}", id, e);
            throw new IllegalArgumentException("附件保存失败，请稍后重试");
        }
        log.info("附件已保存 id={} name={} size={} uploader={}", id, att.originalName, size, uploader);
        return id;
    }

    public Attachment find(String id) {
        return id == null ? null : attachmentMapper.findById(id);
    }

    /** 提交申请成功后回填流程实例 ID，用于审批页按实例反查附件 */
    public void bindBizId(String id, String bizId) {
        if (id != null && bizId != null) {
            attachmentMapper.updateBizId(id, bizId);
        }
    }

    /** 附件在磁盘上的绝对路径 */
    public Path diskPath(Attachment att) {
        return Paths.get(attachmentProperties.getDir()).resolve(att.storedName);
    }

    /** 原文件名做基础清洗：去路径分隔符与控制字符，长度截断到 200 */
    private static String sanitizeName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    /** 原文件名做基础清洗：去路径分隔符与控制字符，长度截断到 200 */
    public void delete(String id) {
        attachmentMapper.deleteById(id);
    }

    /** 根据流程实例 ID 查询附件列表（审批页显示） */
    public List<Attachment> listByBizId(String bizId) {
        return attachmentMapper.listByBizId(bizId);
    }
}
