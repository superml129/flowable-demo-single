package com.sheldon.flowable_demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.sheldon.flowable_demo.mapper.SysPositionMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 岗位服务：查询岗位以及岗位下的人。
 * 岗位表在 userdb（flowable_user）库，和 sys_user 同库，用 @DS 切换。
 *
 * 注意：@DS 只在方法开启事务之前生效。这里的写方法都标了 @Transactional，
 * 数据源切换由 dynamic-datasource 在事务开启时决定，因此本类所有方法都必须在
 * userdb 数据源下执行 —— 类级 @DS("userdb") 已保证这一点。
 */
@Slf4j
@Service
@DS("userdb")
public class SysPositionService {

    @Resource
    private SysPositionMapper sysPositionMapper;

    /**
     * 岗位-成员平铺列表，供同步到 Flowable 候选组使用
     */
    public List<PositionMember> listPositionMembers() {
        List<PositionMember> list = sysPositionMapper.listPositionMembers();
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 查某岗位下的所有用户名
     */
    public List<String> listUsernamesByPosition(String positionCode) {
        List<String> list = sysPositionMapper.listUsernamesByPosition(positionCode);
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 启用状态的岗位列表（设计器「候选岗位」下拉）
     */
    public List<PositionOption> listPositions() {
        List<PositionOption> list = sysPositionMapper.listPositions();
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 岗位列表（管理页，含停用与成员数）
     */
    public List<PositionRow> listPositionRows(String keyword) {
        List<PositionRow> list = sysPositionMapper.listPositionRows(trimToNull(keyword));
        return list != null ? list : new ArrayList<>();
    }

    public PositionRow findById(String id) {
        return id == null ? null : sysPositionMapper.findPositionById(id);
    }

    /**
     * 新增岗位。code 唯一，且必须是合法的 Flowable 组名（字母数字下划线）。
     *
     * @return 新岗位 id
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(PositionForm form) {
        String code = normalizeCode(form.getCode());
        String name = requireText(form.getName(), "岗位名称不能为空");
        if (sysPositionMapper.countByCode(code, null) > 0) {
            throw new IllegalArgumentException("岗位编码 " + code + " 已存在");
        }
        String id = newId();
        sysPositionMapper.insertPosition(id, code, name,
                trimToNull(form.getDescription()), form.getStatus() == null ? 1 : form.getStatus());
        log.info("新增岗位 id={} code={} name={}", id, code, name);
        return id;
    }

    /**
     * 修改岗位。改 code 会影响已部署 BPMN 里的 candidateGroups 匹配，
     * 这里只做提示性校验，不阻止（由调用方决定是否提醒）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, PositionForm form) {
        if (id == null || sysPositionMapper.findPositionById(id) == null) {
            throw new IllegalArgumentException("岗位不存在：" + id);
        }
        String code = normalizeCode(form.getCode());
        String name = requireText(form.getName(), "岗位名称不能为空");
        if (sysPositionMapper.countByCode(code, id) > 0) {
            throw new IllegalArgumentException("岗位编码 " + code + " 已被其他岗位占用");
        }
        sysPositionMapper.updatePosition(id, code, name,
                trimToNull(form.getDescription()), form.getStatus() == null ? 1 : form.getStatus());
        log.info("修改岗位 id={} code={} name={}", id, code, name);
    }

    /**
     * 删除岗位，连带删除其成员关系
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (id == null) {
            throw new IllegalArgumentException("岗位 id 不能为空");
        }
        sysPositionMapper.deleteMembersOfPosition(id);
        sysPositionMapper.deletePosition(id);
        log.info("删除岗位 id={}", id);
    }

    /**
     * 全量重置某岗位的成员。
     *
     * 两条唯一性约束：
     * 1. 一个岗位最多一个主责（primaryId 不在 userIds 里时视为无主责）；
     * 2. 一个人最多在一个岗位上是主责（切岗位时自动清掉原岗位标记）。
     *
     * @param userIds   用户 id 列表，空列表表示清空成员
     * @param primaryId 主责用户 id，可为 null
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMembers(String positionId, List<Integer> userIds, Integer primaryId) {
        if (positionId == null || sysPositionMapper.findPositionById(positionId) == null) {
            throw new IllegalArgumentException("岗位不存在：" + positionId);
        }
        sysPositionMapper.deleteMembersOfPosition(positionId);
        if (userIds == null || userIds.isEmpty()) {
            log.info("岗位 id={} 成员已清空", positionId);
            return;
        }
        for (Integer userId : userIds) {
            if (userId == null) {
                continue;
            }
            // 只有被指定为主责、且确实在本次成员列表里的用户才标记 is_primary=1
            boolean primary = primaryId != null && primaryId.equals(userId);
            if (primary) {
                clearPrimaryForUser(userId, positionId);
            }
            sysPositionMapper.insertMember(userId, positionId, primary ? 1 : 0);
        }
        log.info("岗位 id={} 成员已更新，共 {} 人", positionId, userIds.size());
    }

    /**
     * 把用户在其他岗位上的「主责」标记去掉，保证主责岗位唯一
     */
    private void clearPrimaryForUser(Integer userId, String keepPositionId) {
        String currentPrimary = sysPositionMapper.findPrimaryPositionIdOfUser(userId);
        if (currentPrimary != null && !currentPrimary.equals(keepPositionId)) {
            // 该用户在原主责岗位上保留成员身份，只把 is_primary 置 0
            sysPositionMapper.updateMemberPrimary(userId, currentPrimary, 0);
        }
    }

    public List<PositionUser> listUsersOfPosition(String positionId) {
        List<PositionUser> list = sysPositionMapper.listUsersOfPosition(positionId);
        return list != null ? list : new ArrayList<>();
    }

    public List<PositionUser> listAllUsers() {
        List<PositionUser> list = sysPositionMapper.listAllUsers();
        return list != null ? list : new ArrayList<>();
    }

    // ==================== 内部工具 ====================

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String requireText(String s, String message) {
        String t = trimToNull(s);
        if (t == null) {
            throw new IllegalArgumentException(message);
        }
        return t;
    }

    /**
     * 岗位编码即 Flowable 组名，限制为字母、数字、下划线，避免 BPMN 里写不进候选组
     */
    private static String normalizeCode(String code) {
        String t = requireText(code, "岗位编码不能为空");
        if (!t.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("岗位编码只能包含字母、数字和下划线");
        }
        return t;
    }

    // ==================== 数据传输对象 ====================

    /**
     * 岗位成员（岗位 code + 用户账号），用于同步 Flowable 的组与成员关系。
     * 字段直接 public，供 MyBatis 列映射（positionCode <- positionCode 别名）使用。
     */
    @Data
    public static class PositionMember {
        public String positionCode;
        public String positionName;
        public String username;
        public String realName;
    }

    /**
     * 岗位下拉项。code 即 BPMN 里 flowable:candidateGroups 的取值。
     */
    @Data
    public static class PositionOption {
        public String id;
        public String code;
        public String name;
        public String description;
    }

    /**
     * 岗位管理页的列表行 / 详情
     */
    @Data
    public static class PositionRow {
        public String id;
        public String code;
        public String name;
        public String description;
        public Integer status;
        public Integer memberCount;
    }

    /**
     * 岗位成员（管理页用）
     */
    @Data
    public static class PositionUser {
        public Integer userId;
        public String username;
        public String realName;
        public Integer isPrimary;
    }

    /**
     * 新增/编辑岗位的入参。id 为空表示新增。
     */
    @Data
    public static class PositionForm {
        private String id;
        private String code;
        private String name;
        private String description;
        private Integer status;
    }
}
