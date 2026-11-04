package com.sheldon.flowable_demo.mapper;

import com.sheldon.flowable_demo.service.SysPositionService;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 岗位相关查询（数据源：userdb = flowable_user 库，与 sys_user 同库）
 */
public interface SysPositionMapper {

    /**
     * 岗位-成员平铺列表（只取启用状态的数据）
     */
    @Select("SELECT p.code AS positionCode, p.name AS positionName, " +
            "       u.username AS username, u.real_name AS realName " +
            "FROM sys_position p " +
            "JOIN sys_user_position up ON up.position_id = p.id " +
            "JOIN sys_user u ON u.id = up.user_id " +
            "WHERE p.status = 1 AND u.status = 1 " +
            "ORDER BY p.code, up.is_primary DESC")
    List<SysPositionService.PositionMember> listPositionMembers();

    /**
     * 某岗位下的用户名列表（主责人排在前面）
     */
    @Select("SELECT u.username " +
            "FROM sys_position p " +
            "JOIN sys_user_position up ON up.position_id = p.id " +
            "JOIN sys_user u ON u.id = up.user_id " +
            "WHERE p.code = #{positionCode} AND p.status = 1 AND u.status = 1 " +
            "ORDER BY up.is_primary DESC")
    List<String> listUsernamesByPosition(@Param("positionCode") String positionCode);

    /**
     * 启用状态的岗位列表（设计器「候选岗位」下拉用）
     */
    @Select("SELECT p.id AS id, p.code AS code, p.name AS name, p.description AS description " +
            "FROM sys_position p " +
            "WHERE p.status = 1 " +
            "ORDER BY p.id")
    List<SysPositionService.PositionOption> listPositions();

    // ==================== 岗位管理页 ====================

    /**
     * 岗位列表（管理页用，含停用岗位与成员数量）
     *
     * @param keyword 模糊匹配 code / name，可为空
     */
    @Select("<script>" +
            "SELECT p.id AS id, p.code AS code, p.name AS name, " +
            "       p.description AS description, p.status AS status, " +
            "       (SELECT COUNT(*) FROM sys_user_position up " +
            "          JOIN sys_user u ON u.id = up.user_id " +
            "         WHERE up.position_id = p.id AND u.status = 1) AS memberCount " +
            "FROM sys_position p " +
            "<where>" +
            "  <if test='keyword != null and keyword != \"\"'>" +
            "    (p.code LIKE CONCAT('%', #{keyword}, '%') OR p.name LIKE CONCAT('%', #{keyword}, '%'))" +
            "  </if>" +
            "</where>" +
            "ORDER BY p.status DESC, p.code" +
            "</script>")
    List<SysPositionService.PositionRow> listPositionRows(@Param("keyword") String keyword);

    /**
     * 按主键查岗位
     */
    @Select("SELECT p.id AS id, p.code AS code, p.name AS name, " +
            "       p.description AS description, p.status AS status, " +
            "       (SELECT COUNT(*) FROM sys_user_position up " +
            "          JOIN sys_user u ON u.id = up.user_id " +
            "         WHERE up.position_id = p.id AND u.status = 1) AS memberCount " +
            "FROM sys_position p WHERE p.id = #{id}")
    SysPositionService.PositionRow findPositionById(@Param("id") String id);

    /**
     * 编码是否已被占用（编辑时传 excludeId 排除自身）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_position WHERE code = #{code}" +
            "<if test='excludeId != null and excludeId != \"\"'> AND id != #{excludeId} </if>" +
            "</script>")
    int countByCode(@Param("code") String code, @Param("excludeId") String excludeId);

    @Insert("INSERT INTO sys_position (id, code, name, description, status) " +
            "VALUES (#{id}, #{code}, #{name}, #{description}, #{status})")
    int insertPosition(@Param("id") String id, @Param("code") String code, @Param("name") String name,
                       @Param("description") String description, @Param("status") Integer status);

    @Update("UPDATE sys_position SET code = #{code}, name = #{name}, " +
            "       description = #{description}, status = #{status} " +
            "WHERE id = #{id}")
    int updatePosition(@Param("id") String id, @Param("code") String code, @Param("name") String name,
                       @Param("description") String description, @Param("status") Integer status);

    @Delete("DELETE FROM sys_position WHERE id = #{id}")
    int deletePosition(@Param("id") String id);

    // ==================== 岗位成员 ====================

    /**
     * 岗位下所有成员（管理页勾选用，含 realName）
     */
    @Select("SELECT u.id AS userId, u.username AS username, u.real_name AS realName, " +
            "       up.is_primary AS isPrimary " +
            "FROM sys_user_position up " +
            "JOIN sys_user u ON u.id = up.user_id " +
            "WHERE up.position_id = #{positionId} AND u.status = 1 " +
            "ORDER BY up.is_primary DESC, u.username")
    List<SysPositionService.PositionUser> listUsersOfPosition(@Param("positionId") String positionId);

    /**
     * 全部启用用户（成员分配时的候选池）
     */
    @Select("SELECT u.id AS userId, u.username AS username, u.real_name AS realName " +
            "FROM sys_user u WHERE u.status = 1 ORDER BY u.username")
    List<SysPositionService.PositionUser> listAllUsers();

    @Delete("DELETE FROM sys_user_position WHERE position_id = #{positionId}")
    int deleteMembersOfPosition(@Param("positionId") String positionId);

    @Insert("INSERT INTO sys_user_position (user_id, position_id, is_primary) " +
            "VALUES (#{userId}, #{positionId}, #{isPrimary})")
    int insertMember(@Param("userId") Integer userId, @Param("positionId") String positionId,
                     @Param("isPrimary") Integer isPrimary);

    /**
     * 某用户当前的主责岗位 id（用于「主责」互斥：一人只能有一个主责岗位）
     */
    @Select("SELECT position_id FROM sys_user_position WHERE user_id = #{userId} AND is_primary = 1")
    String findPrimaryPositionIdOfUser(@Param("userId") Integer userId);

    /**
     * 更新成员的主责标记（用于「主责」互斥：换岗位后把原岗位的主责置 0）
     */
    @Update("UPDATE sys_user_position SET is_primary = #{isPrimary} " +
            "WHERE user_id = #{userId} AND position_id = #{positionId}")
    int updateMemberPrimary(@Param("userId") Integer userId, @Param("positionId") String positionId,
                            @Param("isPrimary") Integer isPrimary);
}
