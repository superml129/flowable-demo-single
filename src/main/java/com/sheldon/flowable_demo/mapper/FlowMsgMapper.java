package com.sheldon.flowable_demo.mapper;

import com.sheldon.flowable_demo.entity.FlowMsg;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

/**
 * 注意：本 Mapper 不加 @DS 切换数据源。
 * TASK_COMPLETED 事件在引擎事务（primary 数据源）内同步触发，
 * 事务已开启时 dynamic-datasource 无法切换数据源，写库会落到 primary，
 * 因此 flow_msg 表必须建在 primary（flowable_demo）库中。
 */
public interface FlowMsgMapper {

    @Select("SELECT COUNT(*) FROM flow_msg WHERE receiver=#{receiver} AND is_read=0")
    long countUnRead(@Param("receiver") String receiver);

    @Select("SELECT * FROM flow_msg WHERE receiver=#{receiver} ORDER BY create_time DESC")
    List<FlowMsg> listByUser(@Param("receiver") String receiver);

    @Update("UPDATE flow_msg SET is_read=1 WHERE id=#{id}")
    void readOne(@Param("id") Long id);

    @Update("UPDATE flow_msg SET is_read=1 WHERE receiver=#{receiver} AND is_read=0")
    void readAll(@Param("receiver") String receiver);

    @Insert("INSERT INTO flow_msg(receiver, process_instance_id, process_def_name, task_def_key, task_name, msg_content, is_read, create_time) " +
            "VALUES(#{receiver}, #{processInstanceId}, #{processDefName}, #{taskDefKey}, #{taskName}, #{msgContent}, #{isRead}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FlowMsg msg);
}