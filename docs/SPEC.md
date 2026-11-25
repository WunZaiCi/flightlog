# FlightLog 功能规格

## 页面

| 路径 | 内容 |
|---|---|
| `/login`、`/register` | 登录、注册 |
| `/` | 首页总览：总航班数、坐过的机型数、航司数、机场数、总里程；最近 5 次航班；新解锁的机型 |
| `/flights` | 航班列表：按日期倒序，可按年份、航司、机型、机场筛选和搜索，可以编辑、删除 |
| `/flights/new` | 新增航班（三种方式在同一页切换，见下文「录入」） |
| `/aircraft` | 机型统计 |
| `/map` | 航线地图 |
| `/settings` | 账号信息、API key 状态（只显示有没有配置，不显示 key 本身） |

## 录入

三种方式最后都汇集到**同一个航班表单**，用户确认后才保存：

1. **手动填写**：航班号、日期、出发和到达机场（按 IATA 码、城市名、机场名自动补全）、机型（按 ICAO 代码或名称自动补全，如输入 `787-9` 匹配 `B789`）、注册号、备注。
2. **航班号自动查**：输入航班号和日期 → 调用 AeroDataBox → 预填机场、机型、注册号。查不到或额度用完时提示用户手动填写。
3. **AI 解析**：粘贴一段文字（行程确认邮件、一句话，如“11月3日坐 NH242 从福冈到羽田”），**或上传登机牌、电子客票的截图**，AI 用结构化输出提取出**一个或多个航班**（往返行程会解析成两条），逐条预填表单，用户逐条确认。图片只用于这一次解析，不保存。

表单校验：机场必须在 airports 表里；机型如果不在 aircraft_types 表里，允许保存但标记为“未识别机型”，并在统计页提示。
同一用户、同一天、同一航班号重复录入时提示可能重复。

## 机型统计（`/aircraft`）

- 按 **制造商 → 机型系列 → 具体型号** 分层统计（Boeing → 787 → 787-9），可以展开和折叠。
- 机型排行榜（横向条形图，按乘坐次数）。
- **机型收集墙**：已坐过的机型以卡片显示，带首次乘坐日期和航班号；以后新坐到的机型，显示黄色“新解锁”徽章。
- 每个机型的详情：乘坐次数、总里程、坐过的航司、坐过的注册号列表（同一架飞机坐过多次的，显示次数）。
- 窄体机和宽体机的占比（分段条），按年份的变化趋势。

## 航线地图（`/map`）

- 底图用 OpenFreeMap（免费，不需要 key），加载失败时用离线的 Natural Earth GeoJSON；航线按**大圆弧线**绘制。
- 航线线条的粗细或颜色深浅表示飞过的次数；机场点的大小表示到访次数。
- 可按年份、机型、航司筛选。
- 点击航线显示这条航线的所有航班；点击机场显示该机场的统计。
- 侧栏：总里程（同时换算成绕地球几圈）、最长和最短航线、去过的国家和地区数。

## 数据模型

```
users           id, email (唯一), password_hash, display_name, created_at
SPRING_SESSION, SPRING_SESSION_ATTRIBUTES
                Spring Session JDBC 的标准会话表（结构写在 P2 的 Flyway 迁移里）
flights         id, user_id → users, flight_date, flight_number, airline_iata,
                dep_iata → airports, arr_iata → airports,
                aircraft_type_icao → aircraft_types (可为空), registration (可为空),
                distance_km, notes, source ('manual'|'api'|'ai'；V4 迁移的约束里还允许 'csv'，不再使用),
                created_at, updated_at
                索引: (user_id, flight_date), (user_id, aircraft_type_icao)
airports        iata (主键), icao, name, city, country_code, lat, lon, tz
airlines        iata, icao, name, country_code
aircraft_types  icao_code (主键), manufacturer, family, model, display_name, body ('narrow'|'wide'|'regional')
api_cache       key (主键, 如 'aerodatabox:NH242:2026-11-03'), response_json, fetched_at
```

参考数据来源（放在 `backend/src/main/resources/refdata/`，在 README 里注明来源和许可）：
- 机场：OurAirports `airports.csv`（公有领域），只导入有 IATA 码的机场
- 航司：OpenFlights `airlines.dat`（ODbL 许可）
- 机型：按 ICAO 机型代码整理的 `aircraft_types.json`，先覆盖常见民航客机（波音、空客、巴航工业、ATR、庞巴迪/三菱、中国商飞等），可以继续补充

## API

所有接口都要求登录（auth 接口除外），并且只操作当前用户的数据。

```
POST   /api/auth/register | /api/auth/login | /api/auth/logout
GET    /api/auth/me
GET    /api/flights?year=&airline=&type=&airport=&q=
POST   /api/flights
GET    /api/flights/{id}  |  PUT /api/flights/{id}  |  DELETE /api/flights/{id}
GET    /api/lookup/flight?number=NH242&date=2026-11-03     → AeroDataBox（带缓存）
POST   /api/ai/parse   { text } 或 multipart 图片 → { flights: [...] }   → Gemini 结构化输出
GET    /api/stats/summary
GET    /api/stats/aircraft
GET    /api/stats/routes?year=&type=&airline=
GET    /api/ref/airports?q=  |  /api/ref/aircraft-types?q=  |  /api/ref/airlines?q=
GET    /api/health
GET    /api/config     → { aiEnabled, lookupEnabled }
```

## 开发阶段

每个阶段完成后都可以单独使用。完成的条目打 `[x]`。

### P1 骨架
- [x] backend Maven 项目（含 Maven Wrapper）、Spring Boot、`/api/health`、托管静态前端
- [x] frontend Next.js 16 静态导出、Tailwind v4 主题配色、应用外壳（导航栏）
- [x] SQLite JDBC + Flyway，数据库放在 `data/`，启动时自动迁移
- [x] Dockerfile（多阶段构建）、`compose.yaml`（挂载 `./data`，端口绑定 127.0.0.1）、各系统的启动和停止脚本
- [x] 测试：数据库重启后数据仍在

### P2 用户
- [x] 注册、登录、登出（Cookie 会话），前端登录门禁
- [x] 测试：越权访问返回 404

### P3 航班录入（手动）
- [x] 导入参考数据（机场、航司、机型）
- [x] 航班增删改查、表单自动补全、大圆距离计算、重复提示
- [x] 航班列表页的筛选和搜索
- [x] 首页总览

### P4 机型统计
- [x] 分层统计、排行榜、收集墙、机型详情（含注册号）、窄体和宽体占比

### P5 航线地图
- [x] OpenFreeMap 底图（离线时自动切换）、大圆弧线、按次数显示粗细、筛选、点击查看详情

### P6 航班号自动查询
- [x] AeroDataBox 接入、缓存、额度用完和查不到时的提示

### P7 AI 解析录入
- [x] Gemini 结构化输出解析文字、解析多段航程、逐条确认
- [x] 登机牌和电子客票截图识别

### P8 手机远程访问
- [x] `share-tailscale.ps1` / `unshare-tailscale.ps1` 脚本
- [ ] HTTPS 下 Cookie 加 Secure、登录频率限制
- [ ] 所有页面在手机宽度下检查一遍（底部标签栏、表单、地图）
- [ ] PWA：加 manifest 和图标，可以“添加到主屏幕”

### 以后可以考虑
- 年度回顾页（类似“年度报告”）
- 航司收集、机场收集
- 照片附件
