-- 消息通知表（存放在 flowable_demo 库，与 Flowable 引擎同库）
CREATE TABLE IF NOT EXISTS flow_msg (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  receiver VARCHAR(64) NOT NULL COMMENT '接收人',
  process_instance_id VARCHAR(64) DEFAULT NULL COMMENT '流程实例ID',
  process_def_name VARCHAR(255) DEFAULT NULL COMMENT '流程名称',
  task_def_key VARCHAR(128) DEFAULT NULL COMMENT '节点ID',
  task_name VARCHAR(255) DEFAULT NULL COMMENT '节点名称',
  msg_content VARCHAR(500) DEFAULT NULL COMMENT '消息内容',
  is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_receiver_read (receiver, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程消息通知表';
