package com.sheldon.flowable_demo.mapper;

import com.sheldon.flowable_demo.entity.Attachment;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 附件元信息表（sys_attachment，主库 flowable_demo，不需要 @DS 切换）
 */
public interface AttachmentMapper {

    @Insert("INSERT INTO sys_attachment (id, biz_type, biz_id, original_name, stored_name, size_bytes, uploader, create_time) " +
            "VALUES (#{id}, #{bizType}, #{bizId}, #{originalName}, #{storedName}, #{sizeBytes}, #{uploader}, NOW())")
    int insert(Attachment att);

    @Select("SELECT id, biz_type AS bizType, biz_id AS bizId, original_name AS originalName, " +
            "       stored_name AS storedName, size_bytes AS sizeBytes, uploader, " +
            "       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime " +
            "FROM sys_attachment WHERE id = #{id}")
    Attachment findById(@Param("id") String id);

    @Update("UPDATE sys_attachment SET biz_id = #{bizId} WHERE id = #{id}")
    int updateBizId(@Param("id") String id, @Param("bizId") String bizId);

    @Select("SELECT id, biz_type AS bizType, biz_id AS bizId, original_name AS originalName, " +
            "       stored_name AS storedName, size_bytes AS sizeBytes, uploader, " +
            "       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime " +
            "FROM sys_attachment WHERE biz_id = #{bizId}")
    List<Attachment> listByBizId(@Param("bizId") String procInstId);

    @Delete("DELETE FROM sys_attachment WHERE id = #{id}")
    void deleteById(String id);
}
