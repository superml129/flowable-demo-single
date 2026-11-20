package com.sheldon.flowable_demo.mq;

import com.sheldon.flowable_demo.service.PositionGroupSyncRunner;
import com.sheldon.flowable_demo.service.SessionUserService;
import com.sheldon.flowable_demo.service.SysPositionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 岗位/用户变更广播消费者（场景③）。
 *
 * 背景：多实例部署时，管理员在 A 实例改了岗位成员，B/C 实例的内存态与
 * Flowable 身份表都需要跟着变。所以变更方只发一条广播，每个实例各自消费：
 *
 *   1. 全量对账同步 Flowable 候选组（ACT_ID_GROUP / ACT_ID_MEMBERSHIP）；
 *      直接复用 PositionGroupSyncRunner.sync()——幂等、以业务表为准，
 *      比按 diff 精细同步简单可靠，演示项目没必要做增量。
 *   2. 踢掉受影响用户的 Redis 会话。因为登录时 positionCodes 已固化在
 *      session 里，不踢的话被移出岗位的人要等 session 过期（30 分钟）才失去权限。
 *
 * 消息约定（见 FlowEventProducer.sendPositionChanged）：
 *   USER_CHANGE     bizKey=userId
 *   MEMBER_CHANGE   bizKey=userId
 *   POSITION_CHANGE bizKey=positionId（岗位停用/删除，踢全部现有成员）
 *
 * 注意：onMessage 内部吞掉所有异常，不让消息进入 RocketMQ 无限重试；
 * 同步失败可手动调 /position/sync 补偿。
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = FlowEventProducer.POSITION_TOPIC,
        consumerGroup = "flow-position-consumer-group",
        consumeMode = ConsumeMode.CONCURRENTLY)
public class PositionChangeConsumer implements RocketMQListener<MqMessage> {

    @Resource
    private PositionGroupSyncRunner positionGroupSyncRunner;
    @Resource
    private SysPositionService sysPositionService;
    @Resource
    private SessionUserService sessionUserService;

    @Override
    public void onMessage(MqMessage msg) {
        String eventType = msg == null ? null : msg.getEventType();
        String bizKey = msg == null ? null : msg.getBizKey();
        log.info("收到岗位/用户变更广播 type={} key={}", eventType, bizKey);
        try {
            // 1) 全量对账：把业务表 sys_position / sys_user_position 同步进 Flowable 身份表
            int[] r = positionGroupSyncRunner.sync();
            log.info("Flowable 候选组对账完成：新建组 {}，补成员 {}，清失效 {}",
                    r[0], r[1], r[2]);

            // 2) 踢下线受影响用户，迫使其重新登录、重新加载 positionCodes
            for (String username : resolveAffectedUsernames(eventType, bizKey)) {
                int removed = sessionUserService.kick(username);
                if (removed == 0) {
                    log.info("用户 {} 当前无在线会话，无需踢下线", username);
                }
            }
        } catch (Exception e) {
            log.error("处理岗位/用户变更广播失败 type={} key={}（可手动调 /position/sync 补偿）",
                    eventType, bizKey, e);
        }
    }

    /**
     * 按事件类型解析受影响的用户名集合。
     * 岗位级事件踢现有全部成员；用户级事件只踢该用户。
     */
    private List<String> resolveAffectedUsernames(String eventType, String bizKey) {
        if (bizKey == null || bizKey.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            if ("POSITION_CHANGE".equals(eventType)) {
                return sysPositionService.listMemberUsernamesOfPosition(bizKey);
            }
            // USER_CHANGE / MEMBER_CHANGE：bizKey 是 userId
            Integer userId = Integer.valueOf(bizKey.trim());
            String username = sysPositionService.findUsernameById(userId);
            if (username == null) {
                log.warn("广播携带的 userId={} 查不到账号（可能用户已删除），跳过踢下线", userId);
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            result.add(username);
            return result;
        } catch (NumberFormatException e) {
            log.warn("无法识别的 bizKey={}（应为 userId 或 positionId），跳过踢下线", bizKey);
            return Collections.emptyList();
        }
    }
}
