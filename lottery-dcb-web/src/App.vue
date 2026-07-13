<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from './api'
import { buildTrendRows } from './trend'

const loading = ref(true)
const generating = ref(false)
const refreshing = ref(false)
const errorMessage = ref('')
const infoMessage = ref('')
const history = ref([])
const historyStatus = ref(null)
const statistics = ref(null)
const result = ref(null)
const trendType = ref('red')
const trendLimit = ref(30)
const trendRangeIndex = ref(0)
const trendPage = ref(1)
const trendPageSize = 10
const showOmissions = ref(true)

const form = reactive({
  ticketCount: 1,
  candidatePoolSize: 5000,
  redPoolSize: 9,
  lookback: 100,
  betMode: 'COMPOUND_7_2',
  rotationMode: 'PAIR_COVERAGE',
  blueSelectionMode: 'FREQUENCY_BALANCED',
  seed: null,
  strategy: {
    maxConsecutivePairs: 1,
    maxConsecutiveRun: 2,
    minOddCount: 2,
    maxOddCount: 4,
    minSmallCount: 2,
    maxSmallCount: 4,
    minSum: 75,
    maxSum: 130,
    minZoneCount: 1,
    maxZoneCount: 3,
    minPrimeCount: 1,
    maxPrimeCount: 3,
    minSpan: 18,
    maxGap: 12,
    minAcValue: 5,
    maxAcValue: 10,
    minDistinctTails: 4,
    maxSameTailCount: 2,
    avoidRegularPatterns: true
  }
})

const rotationLabels = {
  NONE: '独立优选',
  BALANCED_COVERAGE: '均衡覆盖矩阵',
  PAIR_COVERAGE: '二码覆盖矩阵'
}

const blueLabels = {
  RANDOM: '独立随机',
  FREQUENCY_BALANCED: '频次平衡',
  COLD_HOT_MIX: '冷热交替'
}

const betModeLabels = {
  STANDARD: '单式 / 旋转矩阵',
  COMPOUND_7_2: '7+2 复式'
}

const strategyCards = [
  { number: '01', title: '限制连号', text: '允许少量二连号，限制连续对数与最长连续段' },
  { number: '02', title: '奇偶平衡', text: '默认保留 2:4、3:3、4:2 三种结构' },
  { number: '03', title: '大小搭配', text: '1–16 为小号，17–33 为大号' },
  { number: '04', title: '控制和值', text: '默认红球和值在 75–130 之间' },
  { number: '05', title: '均衡三区', text: '1–11、12–22、23–33 每区默认 1–3 个' },
  { number: '06', title: '质合搭配', text: '质数与合数同时出现，数字 1 单独处理' },
  { number: '07', title: '规避图案', text: '过滤四项等差、交替间隔和三组对称和' },
  { number: '08', title: '保持间隔', text: '限制号码跨度与相邻最大间隔' },
  { number: '09', title: '控制 AC', text: '不同两两差值数减 5，默认 5–10' },
  { number: '10', title: '分散尾数', text: '至少四种尾数，同尾默认不超过两个' },
  { number: '11', title: '独立蓝球', text: '使用独立随机流，不参与红球指标计算' },
  { number: '12', title: '旋转矩阵', text: '对红球池组合做均衡或二码覆盖选择' }
]

const compoundMode = computed(() => form.betMode === 'COMPOUND_7_2')
const matrixEnabled = computed(() => !compoundMode.value && form.rotationMode !== 'NONE')
const statusTone = computed(() => historyStatus.value?.lastError ? 'warning' : 'success')
const maxRedFrequency = computed(() => {
  const rows = statistics.value?.redFrequencies || []
  return Math.max(1, ...rows.map(item => item.count))
})
const maxBlueFrequency = computed(() => {
  const rows = statistics.value?.blueFrequencies || []
  return Math.max(1, ...rows.map(item => item.count))
})
const historyTableRows = computed(() => history.value.slice(0, 30))
const trendDraws = computed(() => history.value.slice(0, trendLimit.value))
const trendRanges = computed(() => trendType.value === 'red'
  ? [
      { label: '一区 01–11', start: 1, end: 11 },
      { label: '二区 12–22', start: 12, end: 22 },
      { label: '三区 23–33', start: 23, end: 33 }
    ]
  : [
      { label: '一区 01–08', start: 1, end: 8 },
      { label: '二区 09–16', start: 9, end: 16 }
    ])
const activeTrendRange = computed(() => trendRanges.value[trendRangeIndex.value] || trendRanges.value[0])
const trendNumbers = computed(() => Array.from(
  { length: activeTrendRange.value.end - activeTrendRange.value.start + 1 },
  (_, index) => activeTrendRange.value.start + index
))
const trendRows = computed(() => buildTrendRows(
  trendDraws.value,
  trendType.value === 'red' ? 33 : 16,
  draw => trendType.value === 'red' ? draw.redBalls : [draw.blueBall]
))
const trendPageCount = computed(() => Math.max(
  1,
  Math.ceil(trendRows.value.length / trendPageSize)
))
const visibleTrendRows = computed(() => {
  const pageStart = (trendPage.value - 1) * trendPageSize
  const range = activeTrendRange.value
  return trendRows.value
    .slice(pageStart, pageStart + trendPageSize)
    .map(row => ({
      ...row,
      cells: row.cells.slice(range.start - 1, range.end)
    }))
})

watch(trendType, () => {
  trendRangeIndex.value = 0
  trendPage.value = 1
})

watch(trendLimit, () => {
  trendPage.value = 1
})

watch(trendPageCount, pageCount => {
  trendPage.value = Math.min(trendPage.value, pageCount)
})

watch(() => form.betMode, (mode, previousMode) => {
  if (mode === 'COMPOUND_7_2' && (previousMode === 'STANDARD' || form.ticketCount > 10)) {
    form.ticketCount = 1
  }
})

onMounted(loadPage)

async function loadPage() {
  loading.value = true
  errorMessage.value = ''
  try {
    const defaults = await api.getDefaults()
    mergeDefaults(defaults)
    await Promise.all([loadHistory(), loadStatus(), loadStatistics()])
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function mergeDefaults(defaults) {
  Object.assign(form, defaults)
  form.strategy = { ...form.strategy, ...(defaults.strategy || {}) }
  // 后端保留旧客户端的单式默认语义；网页按当前使用习惯默认打开 7+2
  form.betMode = 'COMPOUND_7_2'
  form.ticketCount = 1
}

async function loadHistory() {
  history.value = await api.getHistory(100)
}

async function loadStatus() {
  historyStatus.value = await api.getHistoryStatus()
}

async function loadStatistics() {
  statistics.value = await api.getStatistics(form.lookback)
}

async function refreshHistory() {
  refreshing.value = true
  errorMessage.value = ''
  infoMessage.value = ''
  try {
    const response = await api.refreshHistory()
    if (response.success) {
      infoMessage.value = response.message
    } else {
      errorMessage.value = response.message
    }
    await Promise.all([loadHistory(), loadStatus(), loadStatistics()])
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    refreshing.value = false
  }
}

async function generate() {
  generating.value = true
  errorMessage.value = ''
  infoMessage.value = ''
  try {
    const payload = JSON.parse(JSON.stringify(form))
    if (payload.seed === '' || payload.seed === null) {
      payload.seed = null
    } else {
      payload.seed = Number(payload.seed)
    }
    result.value = await api.generate(payload)
    await loadStatistics()
    requestAnimationFrame(() => {
      document.querySelector('#results')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    generating.value = false
  }
}

async function refreshStatisticsForLookback() {
  errorMessage.value = ''
  try {
    await loadStatistics()
  } catch (error) {
    errorMessage.value = error.message
  }
}

function formatNumber(number) {
  return String(number).padStart(2, '0')
}

function formatTime(value) {
  if (!value) return '尚未成功同步'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function percentage(value) {
  return `${(Number(value || 0) * 100).toFixed(1)}%`
}

function barWidth(count, max) {
  return `${Math.max(4, (count / max) * 100)}%`
}

function changeTrendPage(offset) {
  trendPage.value = Math.min(
    trendPageCount.value,
    Math.max(1, trendPage.value + offset)
  )
}

function trendCellLabel(row, cell) {
  if (cell.hit) {
    return `${row.issue} 第 ${cell.number} 号开出`
  }
  if (!showOmissions.value) {
    return `${row.issue} 第 ${cell.number} 号未开出`
  }
  if (cell.omission === null) {
    return `${row.issue} 第 ${cell.number} 号在窗口起点前状态未知`
  }
  return `${row.issue} 第 ${cell.number} 号遗漏 ${cell.omission} 期`
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <a class="brand" href="#top" aria-label="返回顶部">
        <span class="brand-mark"><i></i><i></i></span>
        <span>
          <strong>lottery-dcb</strong>
          <small>DOUBLE COLOR BALL LAB</small>
        </span>
      </a>
      <nav>
        <a href="#generator">组合生成</a>
        <a href="#statistics">历史统计</a>
        <a href="#trend">号码走势</a>
        <a href="#history">开奖数据</a>
      </nav>
      <div v-if="historyStatus" class="data-state" :class="statusTone">
        <span class="state-dot"></span>
        {{ historyStatus.recordCount }} 期数据
      </div>
    </header>

    <main id="top">
      <section class="hero">
        <div class="hero-copy">
          <p class="eyebrow">CONSTRAINT-BASED NUMBER GENERATOR</p>
          <h1>让每一组号码<br><span>有约束，也有解释</span></h1>
          <p class="hero-description">
            基于历史样本描述与 12 项组合策略，从随机候选中筛选结构均衡的红球，
            再用独立蓝球和旋转矩阵完成多注覆盖
          </p>
          <div class="hero-actions">
            <a class="primary-button" href="#generator">开始生成组合</a>
            <a class="text-button" href="#method">查看策略定义 <span>↘</span></a>
          </div>
        </div>
        <div class="hero-visual" aria-hidden="true">
          <div class="orbit orbit-one"></div>
          <div class="orbit orbit-two"></div>
          <div class="sample-ticket">
            <span>STRUCTURE SAMPLE</span>
            <div class="sample-balls">
              <i>03</i><i>08</i><i>12</i><i>18</i><i>25</i><i>32</i><i class="blue">09</i>
            </div>
            <div class="sample-metrics">
              <b>Σ 98</b><b>AC 9</b><b>2:2:2</b>
            </div>
          </div>
        </div>
      </section>

      <aside class="disclaimer">
        <span class="disclaimer-icon">i</span>
        <p>
          <strong>概率说明</strong>
          双色球每期开奖均为独立随机事件，每一种合法组合的理论概率相同。历史数据和筛选条件不能预测未来，也不会提高某一注的中奖概率
        </p>
        <span class="age-label">理性购彩 · 禁止未成年人购彩</span>
      </aside>

      <div v-if="errorMessage" class="toast error-toast" role="alert">
        <span>!</span>{{ errorMessage }}
        <button type="button" @click="errorMessage = ''">×</button>
      </div>
      <div v-if="infoMessage" class="toast info-toast" role="status">
        <span>✓</span>{{ infoMessage }}
        <button type="button" @click="infoMessage = ''">×</button>
      </div>

      <section id="generator" class="section generator-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">01 / GENERATOR</p>
            <h2>配置组合条件</h2>
          </div>
          <p>所有范围均可调整。条件过窄时，后端会返回冲突原因，不会静默放宽约束</p>
        </div>

        <form class="generator-grid" @submit.prevent="generate">
          <div class="panel base-panel">
            <div class="panel-title">
              <span>基础参数</span>
              <small>OUTPUT & MATRIX</small>
            </div>
            <div class="field-grid">
              <label class="wide-field">
                <span>投注方式</span>
                <select v-model="form.betMode">
                  <option value="COMPOUND_7_2">7+2 复式</option>
                  <option value="STANDARD">单式 / 旋转矩阵</option>
                </select>
              </label>
              <label>
                <span>{{ compoundMode ? '复式组数' : '生成注数' }}</span>
                <input
                  v-model.number="form.ticketCount"
                  type="number"
                  min="1"
                  :max="compoundMode ? 10 : 50"
                />
              </label>
              <label>
                <span>候选池数量</span>
                <input v-model.number="form.candidatePoolSize" type="number" min="100" max="50000" step="100" />
              </label>
              <label v-if="!compoundMode" class="wide-field">
                <span>旋转矩阵</span>
                <select v-model="form.rotationMode">
                  <option value="NONE">独立优选</option>
                  <option value="BALANCED_COVERAGE">均衡覆盖矩阵</option>
                  <option value="PAIR_COVERAGE">二码覆盖矩阵</option>
                </select>
              </label>
              <label v-if="matrixEnabled">
                <span>红球池大小</span>
                <input v-model.number="form.redPoolSize" type="number" min="7" max="12" />
              </label>
              <label>
                <span>历史观察期数</span>
                <input v-model.number="form.lookback" type="number" min="10" max="1000" @change="refreshStatisticsForLookback" />
              </label>
              <label class="wide-field">
                <span>蓝球选择</span>
                <select v-model="form.blueSelectionMode">
                  <option value="RANDOM">独立随机</option>
                  <option value="FREQUENCY_BALANCED">频次平衡</option>
                  <option value="COLD_HOT_MIX">冷热交替</option>
                </select>
              </label>
              <label class="wide-field">
                <span>复现种子 <em>可选</em></span>
                <input
                  v-model="form.seed"
                  type="number"
                  min="-9007199254740991"
                  max="9007199254740991"
                  step="1"
                  placeholder="留空则随机生成"
                />
              </label>
              <div v-if="compoundMode" class="compound-spec wide-field">
                <span><b>7 红</b>任选 6 个</span>
                <i>×</i>
                <span><b>2 蓝</b>任选 1 个</span>
                <strong>每组 14 注 · 28 元</strong>
              </div>
            </div>
          </div>

          <div class="panel strategy-panel">
            <div class="panel-title">
              <span>红球硬约束</span>
              <small>10 RED-BALL FILTERS</small>
            </div>
            <div class="constraint-list">
              <div class="constraint-row">
                <div><b>01</b><span>连号控制</span></div>
                <label>连号对 ≤ <input v-model.number="form.strategy.maxConsecutivePairs" type="number" min="0" max="5" /></label>
                <label>最长段 ≤ <input v-model.number="form.strategy.maxConsecutiveRun" type="number" min="1" max="6" /></label>
              </div>
              <div class="constraint-row">
                <div><b>02</b><span>奇数数量</span></div>
                <label>最少 <input v-model.number="form.strategy.minOddCount" type="number" min="0" max="6" /></label>
                <label>最多 <input v-model.number="form.strategy.maxOddCount" type="number" min="0" max="6" /></label>
              </div>
              <div class="constraint-row">
                <div><b>03</b><span>小号数量</span></div>
                <label>最少 <input v-model.number="form.strategy.minSmallCount" type="number" min="0" max="6" /></label>
                <label>最多 <input v-model.number="form.strategy.maxSmallCount" type="number" min="0" max="6" /></label>
              </div>
              <div class="constraint-row">
                <div><b>04</b><span>和值范围</span></div>
                <label>最小 <input v-model.number="form.strategy.minSum" type="number" min="21" max="183" /></label>
                <label>最大 <input v-model.number="form.strategy.maxSum" type="number" min="21" max="183" /></label>
              </div>
              <div class="constraint-row">
                <div><b>05</b><span>每区数量</span></div>
                <label>最少 <input v-model.number="form.strategy.minZoneCount" type="number" min="0" max="6" /></label>
                <label>最多 <input v-model.number="form.strategy.maxZoneCount" type="number" min="0" max="6" /></label>
              </div>
              <div class="constraint-row">
                <div><b>06</b><span>质数数量</span></div>
                <label>最少 <input v-model.number="form.strategy.minPrimeCount" type="number" min="0" max="6" /></label>
                <label>最多 <input v-model.number="form.strategy.maxPrimeCount" type="number" min="0" max="6" /></label>
              </div>
              <div class="constraint-row">
                <div><b>08</b><span>间隔与跨度</span></div>
                <label>跨度 ≥ <input v-model.number="form.strategy.minSpan" type="number" min="0" max="32" /></label>
                <label>最大间隔 ≤ <input v-model.number="form.strategy.maxGap" type="number" min="1" max="32" /></label>
              </div>
              <div class="constraint-row">
                <div><b>09</b><span>AC 值</span></div>
                <label>最小 <input v-model.number="form.strategy.minAcValue" type="number" min="0" max="10" /></label>
                <label>最大 <input v-model.number="form.strategy.maxAcValue" type="number" min="0" max="10" /></label>
              </div>
              <div class="constraint-row">
                <div><b>10</b><span>尾数分散</span></div>
                <label>尾数种类 ≥ <input v-model.number="form.strategy.minDistinctTails" type="number" min="1" max="6" /></label>
                <label>同尾 ≤ <input v-model.number="form.strategy.maxSameTailCount" type="number" min="1" max="6" /></label>
              </div>
              <label class="switch-row">
                <span><b>07</b> 过滤明显规律图案</span>
                <input v-model="form.strategy.avoidRegularPatterns" type="checkbox" />
                <i></i>
              </label>
            </div>
          </div>

          <button class="generate-button" type="submit" :disabled="generating || loading">
            <span v-if="generating" class="spinner"></span>
            <template v-else><i>→</i> 生成策略组合</template>
          </button>
        </form>
      </section>

      <section v-if="result" id="results" class="section results-section">
        <div class="section-heading result-heading">
          <div>
            <p class="eyebrow">02 / GENERATED SET</p>
            <h2>本次组合结果</h2>
          </div>
          <div class="result-meta">
            <span>{{ betModeLabels[result.betMode] }}</span>
            <span>种子 {{ result.seed }}</span>
            <span v-if="result.betMode === 'STANDARD'">{{ rotationLabels[result.rotationMode] }}</span>
            <span>{{ blueLabels[result.blueSelectionMode] }}</span>
          </div>
        </div>

        <div class="coverage-strip">
          <div>
            <small>红球池</small>
            <div class="mini-ball-list">
              <i v-for="number in result.coverage.redPool" :key="number">{{ formatNumber(number) }}</i>
            </div>
          </div>
          <dl>
            <div><dt>合法候选</dt><dd>{{ result.acceptedCandidateCount }}</dd></div>
            <div><dt>尝试组合</dt><dd>{{ result.attempts }}</dd></div>
            <div v-if="result.betMode === 'COMPOUND_7_2'"><dt>展开注数</dt><dd>{{ result.expandedTicketCount }}</dd></div>
            <div v-else><dt>二码覆盖</dt><dd>{{ percentage(result.coverage.pairCoverageRatio) }}</dd></div>
            <div v-if="result.betMode === 'COMPOUND_7_2'"><dt>合计金额</dt><dd>{{ result.totalStakeAmountYuan }} 元</dd></div>
            <div v-else><dt>覆盖对数</dt><dd>{{ result.coverage.coveredPairCount }}/{{ result.coverage.possiblePairCount }}</dd></div>
          </dl>
        </div>
        <p class="coverage-note">{{ result.coverage.description }}</p>

        <div v-if="result.betMode === 'STANDARD'" class="ticket-grid">
          <article v-for="ticket in result.tickets" :key="ticket.sequence" class="ticket-card">
            <header>
              <span>NO. {{ String(ticket.sequence).padStart(2, '0') }}</span>
              <b>结构分 {{ ticket.score }}</b>
            </header>
            <div class="ticket-balls">
              <i v-for="number in ticket.redBalls" :key="number">{{ formatNumber(number) }}</i>
              <span class="plus">+</span>
              <i class="blue-ball">{{ formatNumber(ticket.blueBall) }}</i>
            </div>
            <div class="highlight-list">
              <span v-for="highlight in ticket.highlights" :key="highlight">{{ highlight }}</span>
            </div>
          </article>
        </div>

        <div v-else class="compound-grid">
          <article
            v-for="group in result.compoundGroups"
            :key="group.sequence"
            class="compound-card"
          >
            <header class="compound-header">
              <div>
                <span>7+2 复式 · 第 {{ group.sequence }} 组</span>
                <small>每个六红子集均通过当前硬约束</small>
              </div>
              <b>结构分 {{ group.score }}</b>
            </header>

            <div class="compound-ball-groups">
              <section>
                <h3>红球 · 7 选 6</h3>
                <div class="compound-balls compound-red">
                  <i v-for="number in group.redBalls" :key="number">{{ formatNumber(number) }}</i>
                </div>
              </section>
              <section>
                <h3>蓝球 · 2 选 1</h3>
                <div class="compound-balls compound-blue">
                  <i v-for="number in group.blueBalls" :key="number">{{ formatNumber(number) }}</i>
                </div>
              </section>
            </div>

            <div class="compound-summary" aria-live="polite">
              <div><strong>{{ group.expandedTicketCount }}</strong><span>注</span></div>
              <div><strong>{{ group.stakeAmountYuan }}</strong><span>元</span></div>
              <small>C(7,6) × C(2,1) = 14 注 · 每注 2 元</small>
            </div>

            <div class="highlight-list">
              <span v-for="highlight in group.highlights" :key="highlight">{{ highlight }}</span>
            </div>

            <details class="expanded-tickets">
              <summary>查看本组展开的 {{ group.expandedTicketCount }} 注单式</summary>
              <div class="expanded-ticket-grid">
                <div
                  v-for="ticket in group.expandedTickets"
                  :key="ticket.sequence"
                  class="expanded-ticket"
                >
                  <small>NO. {{ String(ticket.sequence).padStart(2, '0') }}</small>
                  <div>
                    <i v-for="number in ticket.redBalls" :key="number">{{ formatNumber(number) }}</i>
                    <b>{{ formatNumber(ticket.blueBall) }}</b>
                  </div>
                </div>
              </div>
            </details>
          </article>
        </div>
        <p class="result-notice">{{ result.notice }}</p>
      </section>

      <section id="statistics" class="section statistics-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">03 / HISTORY PROFILE</p>
            <h2>历史样本描述</h2>
          </div>
          <p v-if="statistics">最近 {{ statistics.lookback }} 期 · {{ statistics.notice }}</p>
        </div>

        <div v-if="statistics" class="statistics-grid">
          <div class="panel frequency-panel">
            <div class="panel-title">
              <span>红球出现次数</span>
              <small>1—33</small>
            </div>
            <div class="frequency-grid red-frequency">
              <div v-for="item in statistics.redFrequencies" :key="item.number" class="frequency-item">
                <span>{{ formatNumber(item.number) }}</span>
                <i><b :style="{ width: barWidth(item.count, maxRedFrequency) }"></b></i>
                <em>{{ item.count }}</em>
              </div>
            </div>
          </div>
          <div class="side-statistics">
            <div class="panel compact-panel">
              <div class="panel-title">
                <span>样本两端</span>
                <small>仅作描述</small>
              </div>
              <div class="sample-groups">
                <div>
                  <small>样本内较活跃</small>
                  <p><i v-for="number in statistics.activeRedNumbers" :key="number">{{ formatNumber(number) }}</i></p>
                </div>
                <div>
                  <small>样本内较安静</small>
                  <p class="quiet"><i v-for="number in statistics.quietRedNumbers" :key="number">{{ formatNumber(number) }}</i></p>
                </div>
              </div>
            </div>
            <div class="panel compact-panel">
              <div class="panel-title">
                <span>蓝球出现次数</span>
                <small>1—16</small>
              </div>
              <div class="frequency-grid blue-frequency">
                <div v-for="item in statistics.blueFrequencies" :key="item.number" class="frequency-item">
                  <span>{{ formatNumber(item.number) }}</span>
                  <i><b :style="{ width: barWidth(item.count, maxBlueFrequency) }"></b></i>
                  <em>{{ item.count }}</em>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="trend" class="section trend-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">04 / NUMBER TREND</p>
            <h2>红蓝球走势图</h2>
          </div>
          <p>最新一期在上，命中号码以球标记，遗漏值仅在所选观察窗口内连续计算</p>
        </div>

        <div class="trend-panel">
          <div class="trend-toolbar">
            <div class="trend-tabs" role="group" aria-label="走势图类型">
              <button
                type="button"
                :aria-pressed="trendType === 'red'"
                :class="{ active: trendType === 'red' }"
                @click="trendType = 'red'"
              >
                红球走势
              </button>
              <button
                type="button"
                :aria-pressed="trendType === 'blue'"
                :class="{ active: trendType === 'blue' }"
                @click="trendType = 'blue'"
              >
                蓝球走势
              </button>
            </div>
            <div class="trend-ranges" role="group" aria-label="号码区间">
              <button
                v-for="(range, index) in trendRanges"
                :key="range.label"
                type="button"
                :aria-pressed="trendRangeIndex === index"
                :class="{ active: trendRangeIndex === index }"
                @click="trendRangeIndex = index; trendPage = 1"
              >
                {{ range.label }}
              </button>
            </div>
            <label class="trend-limit">
              <span>观察期数</span>
              <select v-model.number="trendLimit">
                <option :value="20">20 期</option>
                <option :value="30">30 期</option>
                <option :value="50">50 期</option>
                <option :value="100">100 期</option>
              </select>
            </label>
            <label class="trend-check">
              <input v-model="showOmissions" type="checkbox" />
              <span>显示遗漏值</span>
            </label>
            <small>{{ activeTrendRange.label }} · 已载入 {{ trendRows.length }} 期</small>
          </div>

          <div v-if="trendRows.length" class="trend-view">
            <table class="trend-table" :class="`trend-${trendType}`">
              <thead>
                <tr>
                  <th class="trend-issue-col" scope="col">期号</th>
                  <th v-for="number in trendNumbers" :key="number" scope="col">
                    {{ formatNumber(number) }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in visibleTrendRows"
                  :key="row.issue"
                >
                  <th class="trend-issue-col" scope="row">
                    <b>{{ row.issue }}</b>
                    <small>{{ row.drawDate?.slice(5) }}</small>
                  </th>
                  <td
                    v-for="cell in row.cells"
                    :key="cell.number"
                    class="trend-cell"
                    :class="{ hit: cell.hit }"
                    :aria-label="trendCellLabel(row, cell)"
                  >
                    <span v-if="cell.hit">{{ formatNumber(cell.number) }}</span>
                    <em v-else-if="showOmissions && cell.omission !== null">{{ cell.omission }}</em>
                    <em v-else-if="showOmissions">—</em>
                  </td>
                </tr>
              </tbody>
            </table>
            <nav class="trend-pagination" aria-label="走势期数分页">
              <button type="button" :disabled="trendPage === 1" @click="changeTrendPage(-1)">较新</button>
              <span>第 {{ trendPage }} / {{ trendPageCount }} 页</span>
              <button type="button" :disabled="trendPage === trendPageCount" @click="changeTrendPage(1)">更早</button>
            </nav>
          </div>
          <p v-else class="trend-empty">暂无可用于绘制走势的历史数据</p>
        </div>
      </section>

      <section id="history" class="section history-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">05 / DATA ARCHIVE</p>
            <h2>历史开奖配置</h2>
          </div>
          <button
            class="refresh-button"
            type="button"
            :disabled="refreshing || !historyStatus?.updateEnabled"
            @click="refreshHistory"
          >
            <span :class="{ rotating: refreshing }">↻</span>
            {{ !historyStatus?.updateEnabled ? '同步已关闭' : refreshing ? '同步中' : '立即同步' }}
          </button>
        </div>

        <div v-if="historyStatus" class="status-grid">
          <div><small>本地总期数</small><strong>{{ historyStatus.recordCount }}</strong></div>
          <div><small>最新期号</small><strong>{{ historyStatus.latestIssue || '—' }}</strong></div>
          <div><small>最近成功同步</small><strong>{{ formatTime(historyStatus.lastSuccessAt) }}</strong></div>
          <div><small>数据状态</small><strong :class="statusTone">{{ historyStatus.lastError ? '本地降级可用' : '正常可用' }}</strong></div>
        </div>
        <p v-if="historyStatus?.lastError" class="sync-warning">官方接口最近一次同步失败：{{ historyStatus.lastError }}。当前继续使用本地配置数据</p>

        <div class="history-table-wrap">
          <table class="history-table">
            <thead>
              <tr><th>期号</th><th>开奖日期</th><th>红球</th><th>蓝球</th></tr>
            </thead>
            <tbody>
              <tr v-for="draw in historyTableRows" :key="draw.issue">
                <td>{{ draw.issue }}</td>
                <td>{{ draw.drawDate }}</td>
                <td><i v-for="number in draw.redBalls" :key="number">{{ formatNumber(number) }}</i></td>
                <td><i class="history-blue">{{ formatNumber(draw.blueBall) }}</i></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section id="method" class="section method-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">06 / METHOD</p>
            <h2>十二项策略定义</h2>
          </div>
          <p>硬约束负责排除不符合结构的组合，评分负责在合法候选间排序</p>
        </div>
        <div class="method-grid">
          <article v-for="strategy in strategyCards" :key="strategy.number">
            <span>{{ strategy.number }}</span>
            <h3>{{ strategy.title }}</h3>
            <p>{{ strategy.text }}</p>
          </article>
        </div>
      </section>
    </main>

    <footer>
      <div class="brand footer-brand">
        <span class="brand-mark"><i></i><i></i></span>
        <span><strong>lottery-dcb</strong><small>FOR LEARNING & ENTERTAINMENT</small></span>
      </div>
      <p>历史不会预告未来。保持清醒，量力而行</p>
      <span>Spring Boot · Vue · Maven</span>
    </footer>
  </div>
</template>
