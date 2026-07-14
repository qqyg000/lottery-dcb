# lottery-dcb

基于 Spring Boot 4.1 和 Vue 3 的双色球组合约束、7+2 复式、历史统计与旋转矩阵覆盖工具

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

- 启动时从中国福利彩票官网接口增量更新历史开奖数据
- 首次联网时拉取完整可用历史页，后续同步必须衔接到本地边界期号后才写回
- 历史数据保存在 jar 外部的 `./config/ssq-history.json`
- 官方接口不可用时自动降级为本地数据，不影响应用启动
- 奇偶、大小、和值、三区、质合、连号、间隔、AC 值、尾数和规律图案约束
- 蓝球独立随机、频率平衡和冷热混合三种选择方式
- 独立优选、均衡覆盖、二码覆盖三种矩阵模式
- 7 红 2 蓝复式：7 个六红子集全部通过约束后，与 2 个独立蓝球完整展开为 14 注（每组 28 元）
- 组合指标解释、号码覆盖率、二码覆盖率、红蓝球频次统计
- 红球 1–33、蓝球 1–16 的可切换遗漏走势图，支持最近 20/30/50/100 期
- 页面固定为单个浏览器视口，通过顶部导航切换首页、生成、结果、统计、走势、开奖和策略页面
- 不使用任何翻页按钮或分页状态，走势图、历史开奖、策略说明和生成结果完整渲染
- 浏览器页面本身固定为单屏，内容超出时只在当前页面的内容区域内滚动
- 生成参数和统计内容在窄屏使用页内标签切换
- Maven 自动安装固定 Node/npm、编译前端并将产物打入可执行 jar

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

首次 Maven 打包会下载固定版本的 Node.js 和 npm，后续使用本地 Maven 缓存。`clean` 会同时删除旧的前端 `dist` 和项目根目录旧 JAR；每次构建都会生成新的前端构建时间，在 `prepare-package` 阶段复制最新 `dist`。生成 Spring Boot 可执行包后，Maven 会解包校验其中确实包含最新前端，再覆盖项目根目录的 `lottery-dcb.jar` 并输出 SHA-256。`lottery-dcb-service/target/lottery-dcb.jar` 与根目录 JAR 内容相同

Windows 控制台建议使用 `run-windows.cmd` 启动，它会同时设置代码页、JVM 输出编码和日志编码为 UTF-8

在 IDEA 中直接运行 `LotteryDcbApplication` 时，只要之前执行过一次 `mvn clean package`，后端会从 `lottery-dcb-web/dist` 读取前端页面。项目根目录和 `lottery-dcb-service` 模块目录两种 Working directory 都支持

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

此模式请访问 Vite 输出的地址，通常是 `http://localhost:5173`。`http://localhost:8080` 只提供 API。Vite 会把 `/api` 代理到 `http://localhost:8080`

如需直接访问 `http://localhost:8080` 的完整一体化页面，必须执行 `mvn clean package` 后运行生成的 jar，不要添加 `-Dskip.frontend=true`

## 访问与编码排查

- `mvn clean package` 完成后直接运行项目根目录的 `java -jar lottery-dcb.jar`，避免误启动之前复制到其他目录的旧包
- 不存在的静态资源会正常返回 HTTP 404，不再被包装成服务器 500 错误
- 首页和静态资源返回 `Cache-Control: no-store`，重新启动新 jar 后浏览器不会继续使用旧前端
- HTTP 响应和文件日志固定为 UTF-8；直接执行 `java -jar lottery-dcb.jar` 时，程序会在 Spring Boot 日志初始化前按 `System.console()`、`stdout.encoding`、`sun.stdout.encoding`、`native.encoding` 的顺序自动探测终端编码
- 启动第一行会输出 `[lottery-dcb] console charset=编码名称`，用于确认本次实际采用的控制台编码
- IDEA 启动会显示 `launch=IDEA`，并优先使用 `native.encoding` 匹配 IDEA 默认的系统控制台编码；如果 IDEA 的 Console Default Encoding 已手工改为 UTF-8，可在运行配置中设置环境变量 `CONSOLE_LOG_CHARSET=UTF-8`
- 自动探测不符合特殊终端时，可显式设置 `CONSOLE_LOG_CHARSET`。例如 Windows UTF-8 终端先执行 `chcp 65001` 和 `set CONSOLE_LOG_CHARSET=UTF-8`；传统中文代码页可执行 `set CONSOLE_LOG_CHARSET=GBK`
- 打包和启动前请先停止同端口上的旧进程

## 历史数据配置

默认配置位于 `lottery-dcb-service/src/main/resources/application.yml`：

```yaml
lottery:
  history:
    file-path: ${LOTTERY_HISTORY_FILE:./config/ssq-history.json}
    update-enabled: ${LOTTERY_HISTORY_UPDATE_ENABLED:true}
```

可通过环境变量修改文件路径或关闭联网更新。关闭后，启动同步和手动同步都会停用。classpath 中的 seed 文件内置最近 9 期，仅用于首次初始化和离线降级；首次成功联网会按官网页数补全历史记录

`./config` 相对于启动 `java -jar` 命令时的工作目录，并不固定为 jar 所在目录。同步采用有限超时和重试，官网不可用时首次启动可能等待数十秒，随后会继续使用本地文件启动

## 核心接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/history?limit=30` | 查询最近开奖 |
| GET | `/api/history/status` | 查询本地数据与同步状态 |
| POST | `/api/history/refresh` | 手动触发同步 |
| GET | `/api/statistics?lookback=100` | 查询红蓝球频次 |
| GET | `/api/predictions/defaults` | 查询前端默认策略参数 |
| POST | `/api/predictions/generate` | 生成单式、旋转矩阵或 7+2 复式 |

复现种子限制在 JavaScript 安全整数范围 `-9007199254740991` 到 `9007199254740991`，保证浏览器传输后仍可精确复现

`betMode=COMPOUND_7_2` 时，`ticketCount` 表示复式组数（1–10）。每组响应含 7 个红球、2 个蓝球、14 张展开票和 28 元金额；顶层 `tickets` 同时保留全部展开票，便于旧客户端继续读取。未传 `betMode` 的 API 请求仍按原有 `STANDARD` 单式模式处理，网页端则默认选择 7+2

## AC 值定义

6 个红球两两差值去重后的数量减去 5，即：

```text
AC = 不同两两差值个数 - (红球个数 - 1)
```

6 个红球的 AC 值范围为 0–10，默认筛选范围为 5–10

## 数据源说明

历史同步使用中国福利彩票官网往期开奖页面内部 JSON 接口。该接口不是公开承诺稳定性的开放 API，可能出现限流、403 或字段调整，因此项目实现了超时、有限重试、宽松字段解析、原子写文件和本地降级

## 参考资料

- [中国福利彩票双色球游戏规则](https://www.cwl.gov.cn/c/2026/01/29/417937.shtml)
- [中国福利彩票双色球往期开奖](https://www.cwl.gov.cn/ygkj/wqkjgg/)
- [Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot Maven 可执行包](https://docs.spring.io/spring-boot/maven-plugin/packaging.html)
- [Vue 快速上手](https://vuejs.org/guide/quick-start)
- [Maven AntRun Plugin](https://maven.apache.org/plugins/maven-antrun-plugin/)
