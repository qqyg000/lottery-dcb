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

## 已实现功能

- 支持单式、旋转矩阵和可配置复式；复式红球可选 6–33 个、蓝球可选 1–16 个，默认生成 2 组 7+2
- 复式按 `C(红球数, 6) × C(蓝球数, 1)` 展开并计算注数与金额，单次最多展开 10000 注
- 提供奇偶、大小、和值、三区、质合、连号、间隔、AC 值、尾数和规律图案等硬约束
- 支持蓝球随机、频次平衡、冷热交替，以及独立优选、均衡覆盖、二码覆盖三种矩阵模式
- 展示红蓝球频次、组合指标、号码覆盖率、遗漏走势和历史开奖，结果可一键复制
- 启动时增量同步中国福利彩票历史数据；官方接口不可用时自动使用本地数据
- 单屏响应式界面通过顶部导航切换功能，超出内容在当前区域内滚动
- Maven 自动安装固定版本的 Node/npm，完成前后端测试、构建并生成一体化可执行 JAR

## 页面使用说明

- **首页**：配置复式或单式生成参数、历史观察期数、蓝球选择方式和各项硬约束。复式可直接输入红球数和蓝球数，默认打开 `7+2 复式`，复式组数为 `2`
- **生成结果**：生成成功后出现在顶部导航中。可查看组合结构、展开注数、金额与约束摘要，也可复制全部号码
- **历史统计**：查看红球频次、活跃号码、沉寂号码、蓝球频次和样本摘要
- **号码走势**：红球与蓝球分别切换展示；最新一期在上，可显示或隐藏遗漏值
- **开奖数据**：查看当前加载的历史开奖、数据总期数、最新期号、最近同步时间和同步状态，可手动触发更新


## 环境要求

- JDK 17+
- Maven 3.6.3+
- 前端单独开发时需要 Node.js 22.18+

## 一体化打包与运行

在项目根目录执行：

```bash
mvn clean package
java -jar lottery-dcb.jar
```

打开 `http://localhost:8080`

默认端口来自 `SERVER_PORT`，未配置时为 `8080`。需要临时改用其他端口时可执行：

```bash
java -jar lottery-dcb.jar --server.port=8081
```

首次打包会下载固定版本的 Node.js 和 npm，后续使用本地 Maven 缓存。根目录和 `lottery-dcb-service/target` 中都会生成 `lottery-dcb.jar`，根目录版本可直接运行。Windows 建议使用 `run-windows.cmd` 启动；在 IDEA 中运行前，先执行一次 `mvn clean package` 以生成前端资源


## 本地开发

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

此模式通常访问 `http://localhost:5173`，Vite 会把 `/api` 代理到后端 `http://localhost:8080`。如需访问一体化页面，请运行完整打包后的 JAR


## 测试与构建检查

仅检查前端时执行：

```bash
cd lottery-dcb-web
npm ci
npm test
npm run build
```

完整检查并生成根目录可执行包时执行：

```bash
mvn clean package
```

完整 Maven 构建会依次执行前端测试与构建、Java 编译和后端测试，任一步失败都会终止打包


## 访问与编码排查

- 打包前先停止正在占用 `lottery-dcb.jar` 或目标端口的旧进程，完成后运行根目录的新 JAR
- 首页和静态资源使用 `Cache-Control: no-store`，不存在的资源返回 HTTP 404
- HTTP 响应和文件日志使用 UTF-8；启动首行会输出实际控制台编码
- 如需覆盖自动探测结果，可设置 `CONSOLE_LOG_CHARSET=UTF-8` 或 `CONSOLE_LOG_CHARSET=GBK`

## 历史数据配置

默认配置位于 `lottery-dcb-service/src/main/resources/application.yml`：

```yaml
lottery:
  history:
    file-path: ${LOTTERY_HISTORY_FILE:./config/ssq-history.json}
    update-enabled: ${LOTTERY_HISTORY_UPDATE_ENABLED:true}
```

可通过环境变量修改文件路径或关闭联网更新。`./config` 相对于启动命令的工作目录；内置 seed 数据仅用于首次初始化和离线降级，成功联网后会补全历史记录


## 核心接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/history?limit=30` | 查询最近开奖 |
| GET | `/api/history/status` | 查询本地数据与同步状态 |
| POST | `/api/history/refresh` | 手动触发同步 |
| GET | `/api/statistics?lookback=100` | 查询红蓝球频次 |
| GET | `/api/predictions/defaults` | 查询前端默认策略参数 |
| POST | `/api/predictions/generate` | 生成单式、旋转矩阵或可配置复式 |

- `betMode=COMPOUND`：`compoundRedCount` 为红球数（6–33），`compoundBlueCount` 为蓝球数（1–16），`ticketCount` 为复式组数（1–10）
- `6+1` 属于单式；复式至少有一项超过单式数量，单次最多展开 10000 注
- 旧版 `COMPOUND_7_2` 仍兼容；未传 `betMode` 时按 `STANDARD` 处理
- 复现种子必须位于 JavaScript 安全整数范围 `-9007199254740991` 至 `9007199254740991`


## AC 值定义

6 个红球两两差值去重后的数量减去 5，即：

```text
AC = 不同两两差值个数 - (红球个数 - 1)
```

6 个红球的 AC 值范围为 0–10，默认筛选范围为 5–10

## 数据源说明



历史同步使用中国福利彩票官网往期开奖页面内部接口。该接口可能限流或调整字段，项目通过超时、有限重试、原子写入和本地降级保证可用性

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
