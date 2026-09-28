# 彩票预测-双色球

`lottery-dcb` 是基于 Spring Boot 4.1 和 Vue 3 的双色球组合约束、可配置复式、历史统计与旋转矩阵覆盖工具

> 彩票开奖是独立随机事件，历史数据无法预测未来结果。本项目只按指定统计特征筛选随机组合，不提高任意单注的理论中奖概率。仅供学习与娱乐，请理性购彩，禁止未成年人购彩和兑奖

## 工程结构

```text
lottery-dcb
├── lottery-dcb-service   Spring Boot 后端与一体化打包配置
├── lottery-dcb-web       Vue 3 + Vite 前端
├── pom.xml               Maven 聚合工程
└── .gitignore
```

## 主要功能

- 支持单式、旋转矩阵和可配置复式，默认生成 2 组 7+2，自动计算展开注数与金额
- 提供奇偶、大小、和值、三区、质合、连号、间隔、AC 值、尾数和规律图案等硬约束
- 支持蓝球随机、频次平衡、冷热交替，以及独立优选、均衡覆盖、二码覆盖三种矩阵模式
- 复式蓝球跨组优先去重，覆盖全部 16 个号码后均衡复用；矩阵结果展示池内二码、三码与蓝球覆盖
- 展示红蓝球频次、组合指标、号码覆盖率、遗漏走势和历史开奖，结果可一键复制
- 启动时增量同步中国福利彩票历史数据，支持手动更新；接口不可用时使用本地数据

页面通过顶部导航切换首页、生成结果、历史统计、号码走势和开奖数据。首页配置生成参数与约束，生成后可查看号码、注数、金额和覆盖情况

## 快速启动

需要 JDK 17+、Maven 3.6.3+。Maven 自动安装固定版本的 Node.js 和 npm，无需手动安装；前端单独开发时需要 Node.js 22.18+

在项目根目录构建并运行：

```bash
mvn clean package
java -jar lottery-dcb.jar
```

启动后访问 <http://localhost:8080>。默认端口为 `8080`，可通过 `SERVER_PORT` 或启动参数修改：

```bash
java -jar lottery-dcb.jar --server.port=8081
```

构建包含前后端测试与编译，任一步失败都会终止打包。产物为根目录和 `lottery-dcb-service/target` 下的 `lottery-dcb.jar`

Windows 可使用 `run-windows.cmd` 启动；在 IDEA 中运行前，先完整打包一次以生成前端资源

## 本地开发与测试

前后端分离开发时，后端只提供 API：

```bash
mvn -pl lottery-dcb-service spring-boot:run -Dskip.frontend=true
```

前端：

```bash
cd lottery-dcb-web
npm ci
npm run dev
```

访问 <http://localhost:5173>，Vite 将 `/api` 代理到 `http://localhost:8080`。完整页面也可通过打包后的 JAR 访问

在前端目录执行测试与构建检查：

```bash
npm test
npm run build
```

## 算法与回测

AC 值为不同两两差值的个数减去 `(红球个数 - 1)`；6 个红球的 AC 值范围为 0–10，默认筛选范围为 5–10

Windows 在项目根目录执行离线回测：

```powershell
./scripts/Invoke-PredictionBacktest.ps1 -DrawCount 200 -SeedsPerDraw 3
```

默认读取 `config/ssq-history.json`，每期仅使用之前 100 期，比较 8 注二码矩阵、2 组 7+2 复式与相同注数的随机单式，失败期按零命中计入分母。结果保存到 `target/prediction-backtest.json`，可用 `-HistoryFile`、`-OutputFile` 指定输入输出。至少需要 101 期有效数据，内置 seed 数据不足以回测

回测分别统计红蓝球命中与覆盖数量；池内覆盖率不等于中奖概率，同一期多种子也不是独立开奖样本。算法细节、对比结果与限制见 [算法优化与回测记录](docs/algorithm-optimization.md)

## 访问与编码排查

- 打包前先停止正在占用 `lottery-dcb.jar` 或目标端口的旧进程，完成后运行根目录的新 JAR
- HTTP 响应和文件日志使用 UTF-8，启动首行输出实际控制台编码；乱码时可设置 `CONSOLE_LOG_CHARSET=UTF-8` 或 `CONSOLE_LOG_CHARSET=GBK`

## 配置与数据

默认配置位于 `lottery-dcb-service/src/main/resources/application.yml`：

```yaml
lottery:
  history:
    file-path: ${LOTTERY_HISTORY_FILE:./config/ssq-history.json}
    update-enabled: ${LOTTERY_HISTORY_UPDATE_ENABLED:true}
```

通过环境变量可修改路径或关闭联网更新，`./config` 相对于启动命令的工作目录。数据来自中国福利彩票官网往期开奖接口，支持超时重试与本地降级；内置 seed 数据仅用于首次初始化和离线降级，联网成功后补全历史记录

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/history?limit=30` | 查询最近开奖 |
| GET | `/api/history/status` | 查询本地数据与同步状态 |
| POST | `/api/history/refresh` | 手动触发同步 |
| GET | `/api/statistics?lookback=100` | 查询红蓝球频次 |
| GET | `/api/predictions/defaults` | 查询前端默认策略参数 |
| POST | `/api/predictions/generate` | 生成单式、旋转矩阵或可配置复式 |

- `betMode=COMPOUND`：`compoundRedCount` 为红球数（6–33），`compoundBlueCount` 为蓝球数（1–16），`ticketCount` 为复式组数（1–10）
- `6+1` 属于单式；复式至少有一项超过单式数量，按 `C(红球数, 6) × 蓝球数` 展开，单次最多 10000 注
- 旧版 `COMPOUND_7_2` 仍兼容；未传 `betMode` 时按 `STANDARD` 处理
- 复现种子必须位于 JavaScript 安全整数范围 `-9007199254740991` 至 `9007199254740991`
- `coverage` 中的 `coveredTripleCount`、`possibleTripleCount`、`tripleCoverageRatio` 表示池内三码覆盖，分母为当前红球池的 `C(n, 3)`；`uniqueBlueCount` 为整批不同蓝球数，`blueCoverageRatio=uniqueBlueCount/16`，均不代表整体中奖率
- 同一版本、历史数据、参数和种子可复现结果；算法更新后，相同种子的号码可能变化

## 法律免责声明

本项目仅用于技术研究、学习交流与娱乐，不构成任何形式的投注技巧、投资建议、收益承诺或中奖保证。彩票开奖结果具有随机性，任何历史数据、统计分析、号码筛选或组合生成结果均不能预测未来开奖结果，也不会提高任意单注的理论中奖概率

本项目展示的数据可能因数据源延迟、接口调整、网络异常或程序误差而存在遗漏、偏差或失效，使用者应自行核实相关信息并独立判断。因使用或无法使用本项目、依赖本项目生成的内容，或因数据不准确而产生的任何直接或间接损失，项目作者及贡献者在法律允许的范围内不承担责任

使用者应遵守所在国家或地区的法律法规及彩票管理规定，不得将本项目用于非法赌博、欺诈、未成年人购彩或其他违法违规活动。下载、部署或使用本项目即表示使用者已理解并同意自行承担相关风险与责任；如不同意本声明，请停止使用本项目

## 赞助支持

如果这个项目对你有帮助，欢迎赞助。

<table>
  <tr>
    <th>支付宝</th>
    <th>微信</th>
  </tr>
  <tr>
    <td><img src="./docs/images/sponsor-alipay.png" alt="支付宝收款码" width="260"></td>
    <td><img src="./docs/images/sponsor-wechat.png" alt="微信收款码" width="260"></td>
  </tr>
</table>
