# FlightLog — 个人航班机型统计网站

## 项目概述

记录自己坐过的航班（航班号、日期、机场、机型、注册号），统计坐过哪些机型，并在地图上画出飞过的航线。
支持多用户，每个用户只能看到自己的数据。部署在本机（Windows + Docker Desktop），只在 `localhost` 访问；手机通过 Tailscale 远程访问。

- 功能规格、数据模型、API 和开发阶段：@docs/SPEC.md
- 当前进度：`docs/SPEC.md` 的「开发阶段」清单（`[x]` 表示已完成）

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Next.js 16（App Router，`output: 'export'` 静态导出），React 19，TypeScript strict，Tailwind CSS v4 |
| 前端库 | TanStack Query（请求和缓存）、ECharts（统计图表）、MapLibre GL JS（航线地图） |
| 后端 | Java 25（LTS），Spring Boot 4.x 最新稳定版，Maven（使用 Maven Wrapper `mvnw`） |
| 后端模块 | Spring Web MVC、Spring Security、Spring Session JDBC、Spring AI、Bean Validation |
| 数据库 | SQLite（`org.xerial:sqlite-jdbc`）+ Spring `JdbcClient` + Flyway 迁移，**数据持久化**（见下文）。不用 JPA/Hibernate |
| AI | Spring AI 的 Google GenAI starter（`spring-ai-starter-model-google-genai`），调用 Gemini API **免费档**的 Flash 模型（支持看图），用结构化输出 |
| 航班数据 | AeroDataBox API **免费档**（按航班号和日期查机型、注册号），用 Spring `RestClient` 调用，结果缓存在数据库里 |
| 地图底图 | OpenFreeMap 免费矢量瓦片（不需要 key）；联网失败时自动切换到离线的 Natural Earth 底图 |
| 打包 | 单个 Docker 容器（多阶段构建：Node 构建前端 → Maven 构建 jar → JRE 运行），用 `compose.yaml` 管理 |

**所有外部服务只用免费档，不要引入任何需要付费或绑定信用卡的服务。**

- 新建项目时用 start.spring.io 当时的最新稳定版本。依赖版本交给 Spring Boot 的 BOM 和 Spring AI 的 BOM 管理，`pom.xml` 里不要单独写这些依赖的版本号。前端用 `package-lock.json` 锁定版本。
- 新增依赖前先确认有没有必要，能用 JDK 或 Spring 自带功能解决的就不加。

## 目录结构

```
backend/                            Spring Boot 应用（Maven 项目）
  src/main/java/com/flightlog/      按功能分包：auth/ flight/ stats/ reference/ lookup/ ai/ common/
  src/main/resources/db/migration/  Flyway 迁移文件（V1__init.sql ...）
  src/main/resources/refdata/       离线参考数据（机场、航司、机型），首次启动时导入数据库
  src/test/java/                    测试
frontend/                           Next.js 应用，构建输出到 frontend/out，打包时复制进 jar 的 static/
data/                               SQLite 数据库和备份（挂载进容器，已加入 .gitignore，绝不提交）
scripts/                            启动、停止、备份、Tailscale 脚本
docs/                               SPEC.md 等文档
```

## 常用命令

```powershell
# 后端（在 backend/ 下，Windows 用 .\mvnw.cmd，Mac/Linux 用 ./mvnw）
.\mvnw.cmd spring-boot:run          # 开发服务器 http://localhost:8000（自动读取根目录 .env）
.\mvnw.cmd test                     # 单元测试和接口测试
.\mvnw.cmd verify                   # 测试 + 格式检查（Spotless）+ 打包
.\mvnw.cmd spotless:apply           # 自动格式化代码

# 前端（在 frontend/ 下）
npm ci
npm run dev        # 开发服务器 http://localhost:3000，/api 代理到 8000
npm run build      # 静态导出到 frontend/out
npm test           # Vitest + Testing Library
npm run e2e        # Playwright 端到端测试（桌面 + 手机视口）
npm run lint && npm run typecheck

# 整体运行（Docker）
scripts/start-windows.ps1  /  scripts/stop-windows.ps1
scripts/start-mac.sh       /  scripts/stop-mac.sh
scripts/start-linux.sh     /  scripts/stop-linux.sh
scripts/backup-windows.ps1     # 把 data/flightlog.db 复制到 data/backups/，带时间戳
scripts/share-tailscale.ps1    # 检查 Tailscale 已登录，执行 tailscale serve --bg 8000，打印手机访问地址
scripts/unshare-tailscale.ps1  # 停止分享（tailscale serve reset）
```

## 技术设计规则

### 数据库

- **数据持久化**：数据库文件是 `data/flightlog.db`，通过 compose 挂载到容器的 `/app/data`。启动时只由 Flyway 执行迁移，**绝不删库重建**。
- 表结构的任何改动都要新建一个 Flyway 迁移文件（`V<序号>__<说明>.sql`）。**已经提交过的迁移文件绝对不要修改**，否则 Flyway 的校验和会对不上，应用启动失败。
- 不要用 `spring.sql.init`（schema.sql / data.sql），表结构只由 Flyway 管理。
- SQLite 连接设置：开启 WAL 模式、外键约束（`foreign_keys=ON`）、`busy_timeout=5000`。HikariCP 连接池设小一些（`maximum-pool-size` 为 4 左右），因为 SQLite 同一时间只允许一个写入。
- 所有 SQL 都用 `JdbcClient` 加参数绑定，**禁止拼接字符串生成 SQL**。
- 启动时检查参考数据表（airports、airlines、aircraft_types），为空才导入，已有数据不覆盖。

### 后端代码规范

- 请求和响应的 DTO 用 Java `record`，配合 Bean Validation 注解（`@NotBlank`、`@Pattern` 等）校验输入。
- 分层：Controller → Service → Repository（用 `JdbcClient` 写的类）。Controller 里不写业务逻辑，也不写 SQL。
- 错误统一用 `@RestControllerAdvice` 处理，返回 RFC 9457 的 `ProblemDetail` 格式；错误信息用中文，前端可以直接显示给用户。
- 构造器注入，不用字段上的 `@Autowired`。
- 代码格式用 Spotless + google-java-format，`mvnw verify` 时检查格式。

### Web 和部署

- 前端静态构建后放进 Spring Boot 的 `static/` 目录由后端托管，前端和 API 同源。所有 API 路径以 `/api/` 开头；非 `/api/` 的路径找不到时返回对应的 `.html` 或 `index.html`，让前端路由正常工作。
- 后端端口设为 8000（`server.port=8000`），运行在 http://localhost:8000 。
- 容器端口只绑定本机：`127.0.0.1:8000:8000`。**不要改成 `0.0.0.0`。**
- 手机远程访问走 Tailscale：用 `tailscale serve --bg 8000` 把本机 8000 端口通过 HTTPS 分享到自己的 tailnet（地址形如 `https://<电脑名>.<tailnet>.ts.net`）。不使用 Funnel，不对公网开放。
- 设置 `server.forward-headers-strategy=framework`，让 Spring 正确识别 Tailscale 转发过来的 HTTPS 请求。
- 运行镜像用 Eclipse Temurin 25 JRE，以非 root 用户运行。

### 认证

- 用 Spring Security。注册和登录做成 JSON API（`/api/auth/*`），不用 Spring 自带的登录页面。
- 密码用 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`（默认 bcrypt）哈希。
- 会话用 Spring Session JDBC 存在 SQLite 里（会话表的结构写在 Flyway 迁移里），容器重启后用户不需要重新登录。
- 会话 Cookie 设为 **HttpOnly + SameSite=Lax**；请求是 HTTPS 时（经 Tailscale 访问）自动加 `Secure`，本机 http://localhost 访问时不加。
- 开启 CSRF 保护，用 `CookieCsrfTokenRepository` 加上前端在请求头里带 token 的方式（适合前后端分离的单页应用）。
- 登录接口要限制频率：同一 IP 连续失败 5 次后锁定 15 分钟（用内存计数即可，不引入额外依赖）。
- 每个航班查询都要按 `user_id` 过滤；访问别人的数据一律返回 404。这条必须有测试覆盖。

### 外部 API

- 密钥放在项目根目录 `.env`：`GEMINI_API_KEY`、`AERODATABOX_API_KEY`、`LLM_MODEL`（例如 `gemini-3.8-flash`）。用 `spring.config.import=optional:file:../.env[.properties]` 读取 `.env`；Docker 里用 compose 的 `env_file` 传入。`.env` 不提交，提供 `.env.example`。
- Gemini 的模型名经常变，**不要在代码里写死模型名**，统一通过 `spring.ai.google.genai.chat.model=${LLM_MODEL}` 配置。
- AI 解析用 Spring AI 的 `ChatClient`，用 `.entity(...)` 直接返回 Java record（结构化输出）。
- 免费档有每分钟和每天的请求上限：遇到 429 时要友好提示“今天的免费额度用完了，请手动填写或稍后再试”，不要自动重试轰炸。
- **任何一个 key 缺失时，应用照常启动，对应功能在界面上禁用并给出提示，其他功能照常可用**，手动录入永远可用。
- AeroDataBox 免费额度有限：同一航班号加日期的查询结果缓存在 `api_cache` 表里，不重复请求。
- AI 和 API 返回的结果**只作为表单预填**，必须由用户确认后才保存。
- 测试中禁止真实调用外部 API：AeroDataBox 用 WireMock 模拟，AI 用 mock 的 `ChatModel`。

### 数据规范

- 航班号统一存成大写、不带空格（`nh 242` → `NH242`）；机场用 IATA 三字码；机型用 ICAO 机型代码（`B789`、`A359`）；注册号统一大写（`JA891A`）。
- 日期用出发地当地日期（`YYYY-MM-DD`，Java 里用 `LocalDate`），时间戳一律存 UTC（`Instant`）。
- 航线距离用两个机场坐标按大圆距离（haversine）在后端计算，单位公里，保存航班时写入。

## UI 规范

- 界面语言：简体中文。
- **移动端优先的响应式布局**：所有页面在 390px 宽的手机屏幕上都要好用。手机上导航栏改成底部标签栏，录入表单单列显示，地图全屏显示、筛选器收进抽屉。按钮等点击区域不小于 44px。
- 截图识别要支持手机直接拍照上传（`<input type="file" accept="image/*" capture="environment">`）。
- 配色：
  - 强调色（黄色）`#ecad0a`：新解锁机型的徽章、高亮
  - 主蓝色 `#209dd7`：主操作按钮、链接、地图上的航线
  - 次要色（紫色）`#753991`：提交和保存按钮
  - 深海军蓝 `#032147`：标题、导航栏
  - 灰色文本 `#888888`：次要信息
- 配色定义成 Tailwind v4 的主题变量（`@theme`），不要在组件里写死颜色值。
- 字体通过 `@fontsource` 自托管，构建过程不依赖 Google Fonts，中文回退到系统字体。
- 地图底图默认使用 OpenFreeMap 的样式 `https://tiles.openfreemap.org/styles/positron`（风格简洁，适合叠加航线），并保留 OpenStreetMap 署名。样式加载失败时切换到打包进项目的 Natural Earth GeoJSON 底图，保证离线也能看航线。

## 开发流程

实现一个功能时：
1. 在 `docs/SPEC.md` 里找到对应条目；规格不清楚就先问我，不要自己猜。
2. 先简要说明实现方案，再写代码。
3. **先写测试再写实现**：
   - 后端：JUnit 5 + AssertJ 写单元测试；`@SpringBootTest` + MockMvc 写接口测试（用临时目录里的 SQLite 文件，跑真实的 Flyway 迁移）。
   - 前端：Vitest + Testing Library。
   - 主要流程（注册登录、录入航班、看统计）用 Playwright 写端到端测试，同时跑桌面和手机（390px）两种视口。
4. 跑通全部检查：`mvnw verify`、前端测试、lint、类型检查、`npm run build`。有失败就修，不允许跳过测试、加 `@Disabled` 或删掉测试来让检查通过。
5. 涉及界面的改动，启动开发服务器，在浏览器里实际看一遍（桌面和手机宽度都要看）。
6. **代码审查**：调用 `code-reviewer` 子代理审查本次改动。必须修的问题修完后重新跑第 4 步；建议类问题列出来给我决定。
7. 在 `docs/SPEC.md` 里把完成的条目勾上 `[x]`。
8. git commit（Conventional Commits 格式，如 `feat: 机型统计页`），然后 `git push` 到 GitHub 私有仓库的 `main` 分支。不开 PR。
   - 推送前确认 `.env`、`data/` 没有被暂存。
   - 第一次推送时如果还没有远程仓库，用 `gh repo create flightlog --private --source=. --push` 创建。

## 注意事项

- 不要提交 `.env`、`data/`、`node_modules/`、`frontend/out/`、`backend/target/`。
- 不要修改 `refdata/` 里的原始参考数据文件；需要修正时写在单独的补丁文件里（例如 `aircraft_types_overrides.json`）。
- 有中国大陆网络问题时，Docker 构建可以传 `--build-arg NPM_REGISTRY=https://registry.npmmirror.com`。
- Windows 脚本是 PowerShell，要兼容 Windows PowerShell 5.1 和 PowerShell 7；脚本开头检查 Docker Desktop 是否在运行。
