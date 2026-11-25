package com.sheldon.flowable_demo.controller;

import com.sheldon.flowable_demo.entity.Attachment;
import com.sheldon.flowable_demo.service.AttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 附件预览与下载。
 *
 * 预览原理：返回 Content-Type: application/pdf 的字节流，
 * 浏览器内置 PDF 阅读器直接渲染（auditTask.html 用 iframe 嵌入），
 * 不需要引入 pdf.js 之类的第三方库。
 *
 * 权限：本路径未加入 LoginInterceptor 白名单，未登录一律被拦截；
 * demo 不做"流程参与人"级别的细粒度校验，需要时在 render 前补一道即可。
 *
 * 说明：文件上传不单独走 /attachment/upload 接口，而是随请假表单
 * （multipart）一起提交到 /leaveApply/submit，保证附件与流程实例的
 * 关联在一个请求里完成，见 LeaveApplyController.submitLeave。
 */
@Slf4j
@RestController
@RequestMapping("/attachment")
public class AttachmentController {

    @Resource
    private AttachmentService attachmentService;

    /** 内嵌预览：Content-Disposition: inline，浏览器标签内直接打开 */
    @GetMapping("/preview/{id}")
    public ResponseEntity<byte[]> preview(@PathVariable String id) {
        return render(id, true);
    }

    /** 下载：Content-Disposition: attachment，触发浏览器另存为 */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        return render(id, false);
    }

    private ResponseEntity<byte[]> render(String id, boolean inline) {
        Attachment att = attachmentService.find(id);
        if (att == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("附件不存在".getBytes(StandardCharsets.UTF_8));
        }
        Path path = attachmentService.diskPath(att);
        if (!Files.exists(path)) {
            log.error("附件元数据存在但磁盘文件丢失 id={} path={}", id, path);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("附件文件已丢失".getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (Exception e) {
            log.error("读取附件失败 id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("附件读取失败".getBytes(StandardCharsets.UTF_8));
        }
        // 中文名用 RFC 5987 编码（filename*=UTF-8''...），避免乱码
        String fileName = URLEncoder.encode(att.getOriginalName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + fileName);
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
