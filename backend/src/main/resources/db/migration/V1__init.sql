-- P1 骨架：应用元数据（键值对）。业务表在后续阶段用新的迁移文件添加。
-- 已提交的迁移文件绝对不要修改。
CREATE TABLE app_meta (
    key        TEXT NOT NULL PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TEXT NOT NULL -- UTC，ISO-8601（Instant.toString()）
);
