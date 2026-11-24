-- P3 参考数据：机场、航司、机型。数据在首次启动时由 ReferenceDataImporter 导入（表为空才导入）。
-- 已提交的迁移文件绝对不要修改。

CREATE TABLE airports (
    iata         TEXT PRIMARY KEY,          -- IATA 三字码
    icao         TEXT,
    name         TEXT NOT NULL,
    city         TEXT,
    country_code TEXT,                      -- ISO 3166-1 alpha-2
    lat          REAL NOT NULL,
    lon          REAL NOT NULL,
    tz           TEXT,                      -- tz database 名称，如 Asia/Tokyo；部分机场没有
    kind         TEXT                       -- OurAirports 类型：large_airport / medium_airport / small_airport ...（搜索排序用）
);
CREATE INDEX airports_icao ON airports (icao);

CREATE TABLE airlines (
    id           INTEGER PRIMARY KEY,       -- OpenFlights 的航司 ID
    iata         TEXT,
    icao         TEXT,
    name         TEXT NOT NULL,
    country_code TEXT
);
CREATE INDEX airlines_iata ON airlines (iata);
CREATE INDEX airlines_icao ON airlines (icao);

CREATE TABLE aircraft_types (
    icao_code    TEXT PRIMARY KEY,          -- ICAO 机型代码，如 B789
    manufacturer TEXT NOT NULL,             -- Boeing
    family       TEXT NOT NULL,             -- 787
    model        TEXT NOT NULL,             -- 787-9
    display_name TEXT NOT NULL,             -- Boeing 787-9
    body         TEXT NOT NULL CHECK (body IN ('narrow', 'wide', 'regional'))
);
