-- P6 外部 API 结果缓存（AeroDataBox 免费额度有限，同一航班号加日期不重复请求）。不按用户区分：航班数据是公开信息。
-- 已提交的迁移文件绝对不要修改。
CREATE TABLE api_cache (
    key           TEXT PRIMARY KEY,   -- 例如 aerodatabox:NH242:2026-11-03
    response_json TEXT,               -- 原始响应；查不到航班时为空
    fetched_at    TEXT NOT NULL       -- UTC，ISO-8601
);
