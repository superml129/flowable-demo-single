package com.sheldon.flowable_demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.IdentityService;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.User;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动时把「岗位」同步成 Flowable 的候选组。
 *
 * Flowable 判断"当前用户是否属于候选组"时，查的是 ACT_ID_MEMBERSHIP（用户-组关系）。
 * 所以业务表 sys_position / sys_user_position 里的数据必须同步进 Flowable 的身份表，
 * 否则 flowable:candidateGroups="POS_DEPT_MANAGER" 永远匹配不到任何人。
 *
 * 设计说明：
 * 1. 组 ID 直接用岗位 code（如 POS_DEPT_MANAGER），与 BPMN 中 candidateGroups 的值一致；
 * 2. 每次启动做增量同步（存在则跳过），不做删除，避免误删手工维护的数据；
 * 3. 同步失败不阻止应用启动（演示环境容错）。
 */
@Slf4j
@Component
public class PositionGroupSyncRunner implements ApplicationRunner {

    @Resource
    private IdentityService identityService;

    @Resource
    private SysPositionService sysPositionService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            sync();
        } catch (Exception e) {
            log.error("岗位同步到 Flowable 候选组失败，可稍后手动调用 /position/sync 重试", e);
        }
    }

    /**
     * 执行同步（以业务表为准的全量对账），返回 {新建组数, 新增成员数, 清理失效成员数}
     *
     * 1. 建组 / 建用户档案 / 补缺失的成员关系（原有逻辑）；
     * 2. 对账删除：引擎中业务岗位组下的成员关系，凡业务表里已不存在的，
     *    一并删除。否则取消分配后旧关系残留，被移除的人仍能认领该岗位的待办（越权）。
     *
     * 注意：对账只针对业务岗位集合内的组，手工在引擎里建的其它组不受影响。
     */
    public int[] sync() {
        int groupCount = 0, memberCount = 0, removedCount = 0;
        List<SysPositionService.PositionMember> members = sysPositionService.listPositionMembers();

        // 业务表期望的 组 -> 成员用户名集合
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        for (SysPositionService.PositionMember pm : members) {
            expected.computeIfAbsent(pm.positionCode, k -> new LinkedHashSet<>())
                    .add(pm.username);
        }

        // 1) 组与用户档案：缺则建；成员关系：缺则补
        for (SysPositionService.PositionMember pm : members) {
            // 1.1 同步组
            Group group = identityService.createGroupQuery().groupId(pm.positionCode).singleResult();
            if (group == null) {
                Group g = identityService.newGroup(pm.positionCode);
                g.setName(pm.positionName);
                g.setType("position");
                identityService.saveGroup(g);
                groupCount++;
            }

            // 1.2 同步用户（Flowable 的用户表与 sys_user 是两套，这里按需创建）
            User user = identityService.createUserQuery().userId(pm.username).singleResult();
            if (user == null) {
                User u = identityService.newUser(pm.username);
                u.setFirstName(pm.realName != null ? pm.realName : pm.username);
                identityService.saveUser(u);
            }

            // 1.3 同步成员关系
            boolean exists = identityService.createUserQuery()
                    .userId(pm.username)
                    .memberOfGroup(pm.positionCode)
                    .count() > 0;
            if (!exists) {
                identityService.createMembership(pm.username, pm.positionCode);
                memberCount++;
            }
        }

        // 2) 对账删除：引擎里业务岗位组下的成员，业务表已移除的同步删除
        for (Map.Entry<String, Set<String>> entry : expected.entrySet()) {
            Set<String> expectUsers = entry.getValue();
            for (User u : identityService.createUserQuery().memberOfGroup(entry.getKey()).list()) {
                if (!expectUsers.contains(u.getId())) {
                    identityService.deleteMembership(u.getId(), entry.getKey());
                    removedCount++;
                }
            }
        }

        log.info("岗位同步完成：新建组 {} 个，新增成员关系 {} 条，清理失效成员关系 {} 条",
                groupCount, memberCount, removedCount);
        return new int[]{groupCount, memberCount, removedCount};
    }
}
