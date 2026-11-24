-- P3 航班。每个查询都必须按 user_id 过滤。
-- 已提交的迁移文件绝对不要修改。

CREATE TABLE flights (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id            INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    flight_date        TEXT    NOT NULL,                       -- 出发地当地日期 YYYY-MM-DD
    flight_number      TEXT    NOT NULL,                       -- 大写、无空格，如 NH242
    airline_iata       TEXT,                                   -- 由航班号前缀推出
    dep_iata           TEXT    NOT NULL REFERENCES airports (iata),
    arr_iata           TEXT    NOT NULL REFERENCES airports (iata),
    aircraft_type_icao TEXT,                                   -- 可为空；不在 aircraft_types 表里时是「未识别机型」，所以不加外键
    registration       TEXT,                                   -- 大写，如 JA891A
    distance_km        INTEGER NOT NULL,                       -- 大圆距离，保存时计算
    notes              TEXT,
    source             TEXT    NOT NULL CHECK (source IN ('manual', 'api', 'ai', 'csv')),
    created_at         TEXT    NOT NULL,                       -- UTC，ISO-8601
    updated_at         TEXT    NOT NULL
);

CREATE INDEX flights_user_date ON flights (user_id, flight_date);
CREATE INDEX flights_user_type ON flights (user_id, aircraft_type_icao);
CREATE INDEX flights_user_number ON flights (user_id, flight_number);
