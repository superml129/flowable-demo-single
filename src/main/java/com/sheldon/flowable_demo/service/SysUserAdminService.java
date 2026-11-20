package com.sheldon.flowable_demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.sheldon.flowable_demo.common.AuditEventType;
import com.sheldon.flowable_demo.mapper.SysUserAdminMapper;
import com.sheldon.flowable_demo.mq.FlowEventProducer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理服务（后台增删改查）。
 *
 * 注意与 SysUserService 的分工：那个是登录校验，这个是管理页的 CRUD。
 * 表 sys_user 在 userdb（flowable_user）库。
 */
@Slf4j
@Service
@DS("userdb")
public class SysUserAdminService {

    @Resource
    private SysUserAdminMapper sysUserAdminMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private FlowEventProducer producer;

    public List<UserRow> listUsers(String keyword, Integer status) {
        List<UserRow> list = sysUserAdminMapper.listUsers(trimToNull(keyword), status);
        return list != null ? list : new ArrayList<>();
    }

    public UserRow findById(Integer id) {
        return id == null ? null : sysUserAdminMapper.findById(id);
    }

    public List<UserPosition> listPositionsOfUser(Integer userId) {
        List<UserPosition> list = sysUserAdminMapper.listPositionsOfUser(userId);
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 新增用户
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer create(UserForm form) {
        String username = normalizeUsername(form.getUsername());
        String password = requireText(passwordEncoder.encode(form.getPassword()), "密码不能为空");
        if (sysUserAdminMapper.countByUsername(username, null) > 0) {
            throw new IllegalArgumentException("用户名 " + username + " 已存在");
        }
        UserRow row = new UserRow();
        row.username = username;
        row.password = password;
        row.realName = trimToNull(form.getRealName());
        row.status = form.getStatus() == null ? 1 : form.getStatus();
        sysUserAdminMapper.insertUser(row);
        log.info("新增用户 id={} username={}", row.id, username);
        return row.id;
    }

    /**
     * 直接更新密码（入参必须是已加密的密文）。
     * 供后台重置密码与一次性明文迁移脚本使用；走本 Service 以落在 userdb 数据源。
     */
    public void updatePassword(Integer id, String passwordHash) {
        sysUserAdminMapper.updatePassword(id, passwordHash);
    }

    /**
     * 修改用户基本信息；密码留空表示不修改
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, UserForm form) {
        UserRow exists = id == null ? null : sysUserAdminMapper.findById(id);
        if (exists == null) {
            throw new IllegalArgumentException("用户不存在：" + id);
        }
        String username = normalizeUsername(form.getUsername());
        if (sysUserAdminMapper.countByUsername(username, id) > 0) {
            throw new IllegalArgumentException("用户名 " + username + " 已被其他用户占用");
        }
        Integer status = form.getStatus() == null ? exists.status : form.getStatus();
        sysUserAdminMapper.updateUser(id, username, trimToNull(form.getRealName()), status);

        // 密码留空 = 不修改；非空则先加密再更新
        // （原写法 encode(form.getPassword()) 在留空时会对 null 抛 NPE、
        //   或把空串算成"空密码的密文"写库，导致留空反而改了密码）
        String rawPwd = trimToNull(form.getPassword());
        if (rawPwd != null) {
            sysUserAdminMapper.updatePassword(id, passwordEncoder.encode(rawPwd));
            log.info("重置用户密码 id={} username={}", id, username);
        }
    }

    /**
     * 删除用户，连带删除其岗位归属
     *
     * @param operator 操作人（当前登录账号），由 Controller 从 session 传入，审计日志记录用
     * @throws IllegalArgumentException 当尝试删除最后一个启用用户时
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id, String operator) {
        UserRow exists = id == null ? null : sysUserAdminMapper.findById(id);
        if (exists == null) {
            throw new IllegalArgumentException("用户不存在：" + id);
        }
        // 保护：不能把系统里的启用用户删光，否则再也无法登录
        List<UserRow> active = sysUserAdminMapper.listUsers(null, 1);
        if (active.size() <= 1 && Integer.valueOf(1).equals(exists.status)) {
            throw new IllegalArgumentException("这是最后一个启用状态的用户，删除后将无法登录，已阻止");
        }
        sysUserAdminMapper.deletePositionsOfUser(id);
        sysUserAdminMapper.deleteUser(id);
        log.info("删除用户 id={} username={} operator={}", id, exists.username, operator);
        // 广播：踢掉被删用户残留的 Redis 会话，并让各实例清理其在候选组中的关系
        producer.sendPositionChanged(id, "USER_CHANGE");
        // 发送岗位变更审计事件：operator 是登录人，被删的账号写在内容里
        String msgContent = "删除用户 " + exists.username + " 及其岗位";
        producer.sendAudit(AuditEventType.AUDIT_POSITION_CHANGE.getEventType(), operator, msgContent);
    }

    /**
     * 全量重置某用户的岗位归属
     *
     * @param operator      操作人（当前登录账号），由 Controller 从 session 传入，审计日志记录用
     * @param positionCodes 岗位 code 列表，空表示清空
     * @param primaryCode   主责岗位 code，可为 null
     */
    @Transactional(rollbackFor = Exception.class)
    public void savePositions(Integer userId, List<String> positionCodes, String primaryCode, String operator) {
        UserRow exists = userId == null ? null : sysUserAdminMapper.findById(userId);
        if (exists == null) {
            throw new IllegalArgumentException("用户不存在：" + userId);
        }
        sysUserAdminMapper.deletePositionsOfUser(userId);
        if (positionCodes == null || positionCodes.isEmpty()) {
            log.info("用户 id={} 岗位归属已清空", userId);
            producer.sendPositionChanged(userId, "USER_CHANGE");
            return;
        }
        String primary = trimToNull(primaryCode);
        // 主责必须在所选岗位里，否则忽略
        if (primary != null && !positionCodes.contains(primary)) {
            primary = null;
        }
        for (String code : positionCodes) {
            String c = trimToNull(code);
            if (c == null) {
                continue;
            }
            sysUserAdminMapper.insertUserPosition(userId, c, c.equals(primary) ? 1 : 0);
        }
        log.info("用户 id={} 岗位归属已更新，共 {} 个", userId, positionCodes.size());
        // 广播用户变更：各实例重新对账 Flowable 候选组，并踢该用户的 Redis 会话，
        // 让它重新登录后拿到最新的 positionCodes。发送失败不影响本次保存。
        producer.sendPositionChanged(userId, "USER_CHANGE");
        // 发送岗位变更审计事件：operator 是登录人，被调整的账号写在内容里
        String msgContent = "更新用户 " + exists.username + " 的岗位归属";
        producer.sendAudit(AuditEventType.AUDIT_POSITION_CHANGE.getEventType(), operator, msgContent);
    }

    // ==================== 内部工具 ====================

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

    private static String normalizeUsername(String username) {
        String t = requireText(username, "用户名不能为空");
        if (t.length() < 2) {
            throw new IllegalArgumentException("用户名至少 2 个字符");
        }
        if (!t.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("用户名只能包含字母、数字和下划线");
        }
        return t;
    }

    // ==================== 数据传输对象 ====================

    /**
     * 用户行。字段 public 供 MyBatis 列映射（别名 realName / positionCount）。
     * password 仅用于新增时写库，接口返回前会被清空。
     */
    @Data
    public static class UserRow {
        public Integer id;
        public String username;
        public String realName;
        public String password;
        public Integer status;
        public Integer positionCount;
    }

    /**
     * 用户所属的岗位
     */
    @Data
    public static class UserPosition {
        public String code;
        public String name;
        public Integer isPrimary;
    }

    /**
     * 新增/编辑用户入参。id 为空表示新增；password 为空表示不修改密码。
     */
    @Data
    public static class UserForm {
        private Integer id;
        private String username;
        private String password;
        private String realName;
        private Integer status;
    }
}
