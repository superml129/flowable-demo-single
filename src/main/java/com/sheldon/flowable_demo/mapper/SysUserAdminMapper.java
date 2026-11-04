package com.sheldon.flowable_demo.mapper;

import com.sheldon.flowable_demo.service.SysUserAdminService;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户管理（数据源：userdb = flowable_user 库）
 *
 * 与 SysUserMapper 的分工：
 * - SysUserMapper 只负责登录校验（按 username 查启用用户）
 * - 本接口负责后台的增删改查，字段更全（含 status、岗位归属）
 */
public interface SysUserAdminMapper {

    /**
     * 用户列表（管理页）
     *
     * @param keyword 模糊匹配 username / real_name，可为空
     * @param status  1启用 0停用，null 为不限
     */
    @Select("<script>" +
            "SELECT u.id AS id, u.username AS username, u.real_name AS realName, " +
            "       u.password AS password, u.status AS status, " +
            "       (SELECT COUNT(*) FROM sys_user_position up WHERE up.user_id = u.id) AS positionCount " +
            "FROM sys_user u " +
            "<where>" +
            "  <if test='keyword != null and keyword != \"\"'>" +
            "    (u.username LIKE CONCAT('%', #{keyword}, '%') " +
            "     OR u.real_name LIKE CONCAT('%', #{keyword}, '%'))" +
            "  </if>" +
            "  <if test='status != null'> AND u.status = #{status} </if>" +
            "</where>" +
            "ORDER BY u.status DESC, u.username" +
            "</script>")
    List<SysUserAdminService.UserRow> listUsers(@Param("keyword") String keyword,
                                                @Param("status") Integer status);

    @Select("SELECT u.id AS id, u.username AS username, u.real_name AS realName, " +
            "       u.password AS password, u.status AS status, " +
            "       (SELECT COUNT(*) FROM sys_user_position up WHERE up.user_id = u.id) AS positionCount " +
            "FROM sys_user u WHERE u.id = #{id}")
    SysUserAdminService.UserRow findById(@Param("id") Integer id);

    /**
     * 用户名是否被占用（编辑时传 excludeId 排除自身）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_user WHERE username = #{username}" +
            "<if test='excludeId != null'> AND id != #{excludeId} </if>" +
            "</script>")
    int countByUsername(@Param("username") String username, @Param("excludeId") Integer excludeId);

    @Insert("INSERT INTO sys_user (username, password, real_name, status) " +
            "VALUES (#{username}, #{password}, #{realName}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(SysUserAdminService.UserRow row);

    /**
     * 修改用户基本信息（不含密码）
     */
    @Update("UPDATE sys_user SET username = #{username}, real_name = #{realName}, " +
            "       status = #{status} WHERE id = #{id}")
    int updateUser(@Param("id") Integer id, @Param("username") String username,
                   @Param("realName") String realName, @Param("status") Integer status);

    @Update("UPDATE sys_user SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") Integer id, @Param("password") String password);

    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int deleteUser(@Param("id") Integer id);

    // ==================== 岗位归属 ====================

    /**
     * 某人所属的全部岗位（含 is_primary）
     */
    @Select("SELECT p.code AS code, p.name AS name, up.is_primary AS isPrimary " +
            "FROM sys_user_position up " +
            "JOIN sys_position p ON p.id = up.position_id " +
            "WHERE up.user_id = #{userId} " +
            "ORDER BY up.is_primary DESC, p.code")
    List<SysUserAdminService.UserPosition> listPositionsOfUser(@Param("userId") Integer userId);

    @Delete("DELETE FROM sys_user_position WHERE user_id = #{userId}")
    int deletePositionsOfUser(@Param("userId") Integer userId);

    /**
     * 按岗位 code 建立归属关系
     */
    @Insert("INSERT INTO sys_user_position (user_id, position_id, is_primary) " +
            "SELECT #{userId}, p.id, #{isPrimary} FROM sys_position p WHERE p.code = #{positionCode}")
    int insertUserPosition(@Param("userId") Integer userId,
                           @Param("positionCode") String positionCode,
                           @Param("isPrimary") Integer isPrimary);

    /**
     * 统计某用户被多少条流程相关的数据引用（这里仅统计岗位归属，删除前提示用）
     */
    @Select("SELECT COUNT(*) FROM sys_user_position WHERE user_id = #{userId}")
    int countPositionsOfUser(@Param("userId") Integer userId);
}
