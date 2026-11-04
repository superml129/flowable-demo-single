-- ============================================================
-- 岗位（候选组）相关表结构
-- 说明：Flowable 的 flowable:candidateGroups 只需要"组名"字符串，
--      这里不强制依赖 ACT_ID_GROUP，组名统一用 sys_position.code。
-- ============================================================

-- 1. 岗位表
CREATE TABLE IF NOT EXISTS sys_position (
  id          VARCHAR(32)  NOT NULL COMMENT '主键',
  code        VARCHAR(64)  NOT NULL COMMENT '岗位编码，用作 Flowable 候选组名(candidateGroup)',
  name        VARCHAR(64)  NOT NULL COMMENT '岗位名称',
  description VARCHAR(255) DEFAULT NULL COMMENT '岗位说明',
  status      TINYINT      DEFAULT 1 COMMENT '1启用 0停用',
  PRIMARY KEY (id),
  UNIQUE KEY uk_position_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位表';

-- 2. 用户-岗位关联表（一人可多岗，is_primary 标记主责岗位）
CREATE TABLE IF NOT EXISTS sys_user_position (
  user_id     INT          NOT NULL COMMENT '用户ID(sys_user.id)',
  position_id VARCHAR(32)  NOT NULL COMMENT '岗位ID(sys_position.id)',
  is_primary  TINYINT      DEFAULT 0 COMMENT '1主责岗位',
  PRIMARY KEY (user_id, position_id),
  KEY idx_position (position_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户岗位关联表';

-- ============================================================
-- 3. 示例数据（按你现有的 sys_user 账号调整）
-- ============================================================
INSERT INTO sys_position (id, code, name, description) VALUES
  ('P001', 'POS_DEPT_MANAGER', '部门经理',   '负责部门内请假初审'),
  ('P002', 'POS_GM_OFFICE',    '总经理办公室', '负责总经理审批等终审事项')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 把已有账号挂到岗位（若某账号不存在，INSERT ... SELECT 会自动跳过）
-- 部门经理岗：zhangsan、lisi
INSERT IGNORE INTO sys_user_position (user_id, position_id, is_primary)
SELECT id, 'P001', 1 FROM sys_user WHERE username IN ('zhangsan', 'lisi');

-- 总经理办公室岗：wangwu（按你实际账号替换）
INSERT IGNORE INTO sys_user_position (user_id, position_id, is_primary)
SELECT id, 'P002', 1 FROM sys_user WHERE username IN ('wangwu', 'admin');

-- ============================================================
-- 验证查询
-- ============================================================
-- SELECT p.code, p.name, u.username, u.real_name, up.is_primary
-- FROM sys_position p
-- JOIN sys_user_position up ON up.position_id = p.id
-- JOIN sys_user u ON u.id = up.user_id
-- ORDER BY p.code, up.is_primary DESC;
