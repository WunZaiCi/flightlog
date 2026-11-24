# 参考数据

首次启动时，如果 `airports`、`airlines`、`aircraft_types` 表为空，就从这里导入；已有数据不覆盖。

**不要修改这里的原始数据文件。** 需要修正时写在单独的补丁文件里（例如 `aircraft_types_overrides.json`）。

| 文件 | 来源 | 许可 | 说明 |
|---|---|---|---|
| `ourairports-airports.csv` | [OurAirports](https://ourairports.com/data/) `airports.csv`（2026-11-25 下载） | 公有领域（Public Domain） | 只保留 `iata_code` 为三个字母的行（9053 个），列和内容不变 |
| `ourairports-countries.csv` | OurAirports `countries.csv`（2026-11-25 下载） | 公有领域 | 用于把航司的国家名换成 ISO 国家代码 |
| `openflights-airports.dat` | [OpenFlights](https://openflights.org/data) `airports.dat`（2026-11-25 下载） | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) | 只用其中的时区（tz database 名称），按 IATA 码匹配 |
| `openflights-airlines.dat` | OpenFlights `airlines.dat`（2026-11-25 下载） | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) | 只导入仍在运营（Active = Y）且有 IATA 或 ICAO 代码的航司 |
| `aircraft_types.json` | 本项目按 ICAO 机型代码（ICAO Doc 8643）整理 | 与本项目相同 | 常见民航客机：波音、空客、巴航工业、ATR、庞巴迪/三菱、德哈维兰、中国商飞等，可以继续补充 |

OpenFlights 数据库依据 Open Database License（ODbL）发布：© OpenFlights.org 贡献者。
