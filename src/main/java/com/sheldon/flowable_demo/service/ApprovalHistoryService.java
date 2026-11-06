package com.sheldon.flowable_demo.service;

import lombok.Getter;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 审批履历公共服务（与具体流程无关，任何流程实例都可以调用）。
 *
 * 数据来源约定：
 * - 已完成节点：Flowable 历史任务表（ACT_HI_TASKINST），按完成时间倒序；
 * - 每个节点的结论与意见：办理完成前以 "[结论] 意见" 格式写入该任务的
 *   历史评论（ACT_HI_COMMENT）。流程变量会被下一节点覆盖，不能用。
 *
 * 页面（auditTask.html）拿到的每条记录包含：节点名、办理人、结论、意见、完成时间。
 * 结论标签支持：通过 / 驳回 / 提交（发起申请节点的自动完成记录）/ 空（历史遗留无记录）。
 */
@Service
public class ApprovalHistoryService {

    @Resource
    private TaskService taskService;
    @Resource
    private HistoryService historyService;

    /** 单条履历记录 */
    @Getter
    public static class ApprovalRecord {
        /** 节点名称 */
        private final String name;
        /** 办理人（发起申请节点 = 流程发起人） */
        private final String assignee;
        /** 结论标签：通过 / 驳回 / 提交 / 空串 */
        private final String result;
        /** 审批意见（提交节点 = 提交说明） */
        private final String comment;
        /** 该节点完成时间 */
        private final Date endTime;

        public ApprovalRecord(String name, String assignee, String result,
                              String comment, Date endTime) {
            this.name = name;
            this.assignee = assignee;
            this.result = result;
            this.comment = comment;
            this.endTime = endTime;
        }
    }

    /**
     * 查询某流程实例已完成的节点履历，按完成时间倒序（最近的在最上面）。
     * 流程刚发起、还没有任何完成节点时返回空列表，页面据此不渲染履历区。
     */
    public List<ApprovalRecord> listFinishedRecords(String processInstanceId) {
        List<ApprovalRecord> records = new ArrayList<>();
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return records;
        }
        List<HistoricTaskInstance> finishedTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
        for (HistoricTaskInstance ht : finishedTasks) {
            // 结论与意见在节点完成前以 "[标签] 意见" 写入任务历史评论，
            // 每个任务各存一条，互不覆盖；只取第一条防重复评论干扰
            String result = "";
            String comment = "";
            for (Comment c : taskService.getTaskComments(ht.getId())) {
                String msg = c.getFullMessage();
                if (msg != null && msg.startsWith("[")) {
                    int idx = msg.indexOf(']');
                    if (idx > 0) {
                        result = msg.substring(1, idx);
                        comment = idx + 1 < msg.length() ? msg.substring(idx + 1).trim() : "";
                        break;
                    }
                }
            }
            records.add(new ApprovalRecord(ht.getName(), ht.getAssignee(),
                    result, comment, ht.getEndTime()));
        }
        return records;
    }
}
