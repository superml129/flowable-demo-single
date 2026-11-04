-- ============================================================
-- 岗位成员主责标记清理
-- 背景：一个岗位应最多一个主责人（is_primary=1），
--       一人在多个岗位时也最多有一个主责岗位。
--       初始化的种子数据里 POS_DEPT_MANAGER 有两人同时标了主责。
-- 执行库：flowable_user
-- ============================================================

-- 1. 同一岗位有多个主责时，只保留 user_id 最小的那个
UPDATE sys_user_position up
JOIN (
    SELECT position_id, MIN(user_id) AS keep_user_id
    FROM sys_user_position
    WHERE is_primary = 1
    GROUP BY position_id
    HAVING COUNT(*) > 1
) t ON t.position_id = up.position_id
SET up.is_primary = 0
WHERE up.is_primary = 1
  AND up.user_id <> t.keep_user_id;

-- 2. 同一人在多个岗位上是主责时，只保留 user_id 对应最早的那条
--    （MySQL 不允许在 UPDATE 子查询里直接查同一张表，用派生表包一层）
UPDATE sys_user_position up
JOIN (
    SELECT user_id, MIN(position_id) AS keep_position_id
    FROM (
        SELECT user_id, position_id FROM sys_user_position WHERE is_primary = 1
    ) x
    GROUP BY user_id
    HAVING COUNT(*) > 1
) t ON t.user_id = up.user_id
SET up.is_primary = 0
WHERE up.is_primary = 1
  AND up.position_id <> t.keep_position_id;

-- 3. 查看结果
SELECT p.code AS position_code, p.name AS position_name,
       u.username, u.real_name, up.is_primary
FROM sys_user_position up
JOIN sys_user u ON u.id = up.user_id
JOIN sys_position p ON p.id = up.position_id
ORDER BY p.code, up.is_primary DESC, u.username;
