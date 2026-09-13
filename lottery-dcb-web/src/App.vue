<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from './api'
import { formatResultForCopy } from './result-copy'
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
const activeView = ref('generator')
const generatorPane = ref('base')
const statisticsPane = ref('red')
const expandedGroup = ref(null)
const trendType = ref('red')
const trendLimit = ref(30)
const showOmissions = ref(true)
const copyStatus = ref('idle')
const appBuildTime = __APP_BUILD_TIME__
let copyStatusTimer

const form = reactive({
  ticketCount: 2,
  candidatePoolSize: 5000,
  redPoolSize: 9,
  lookback: 100,
  betMode: 'COMPOUND',
  compoundRedCount: 7,
  compoundBlueCount: 2,
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
  COMPOUND: '复式',
  COMPOUND_7_2: '7+2 复式'
}

const baseNavItems = [
  { id: 'generator', label: '首页' },
  { id: 'statistics', label: '历史统计' },
  { id: 'trend', label: '号码走势' },
  { id: 'history', label: '开奖数据' }
]

const compoundMode = computed(() => isCompoundMode(form.betMode))
const matrixEnabled = computed(() => !compoundMode.value && form.rotationMode !== 'NONE')
const compoundRedCombinationCount = computed(() => combinations(form.compoundRedCount, 6))
const compoundExpandedPerGroup = computed(() => (
  compoundRedCombinationCount.value * Number(form.compoundBlueCount || 0)
))
const compoundTotalExpanded = computed(() => (
  compoundExpandedPerGroup.value * Number(form.ticketCount || 0)
))
const compoundConfigurationInvalid = computed(() => (
  Number(form.compoundRedCount) === 6 && Number(form.compoundBlueCount) === 1
))
const compoundLimitExceeded = computed(() => compoundTotalExpanded.value > 10000)
const navItems = computed(() => {
  if (!result.value) return baseNavItems
  return [
    ...baseNavItems.slice(0, 1),
    { id: 'results', label: '生成结果' },
    ...baseNavItems.slice(1)
  ]
})
const statusTone = computed(() => historyStatus.value?.lastError ? 'warning' : 'success')
const maxRedFrequency = computed(() => {
  const rows = statistics.value?.redFrequencies || []
  return Math.max(1, ...rows.map(item => item.count))
})
const maxBlueFrequency = computed(() => {
  const rows = statistics.value?.blueFrequencies || []
  return Math.max(1, ...rows.map(item => item.count))
})
const historyRows = computed(() => history.value)
const trendDraws = computed(() => history.value.slice(0, trendLimit.value))
const trendNumbers = computed(() => Array.from(
  { length: trendType.value === 'red' ? 33 : 16 },
  (_, index) => index + 1
))
const trendRows = computed(() => buildTrendRows(
  trendDraws.value,
  trendType.value === 'red' ? 33 : 16,
  draw => trendType.value === 'red' ? draw.redBalls : [draw.blueBall]
))

watch(activeView, () => {
  expandedGroup.value = null
})

watch(() => form.betMode, (mode, previousMode) => {
  if (isCompoundMode(mode)) {
    form.seed = null
    if (previousMode === 'STANDARD' || form.ticketCount > 10) {
      form.ticketCount = 2
    }
  }
})

onMounted(() => {
  loadPage()
})

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
  form.betMode = 'COMPOUND'
  form.ticketCount = 2
  form.compoundRedCount = 7
  form.compoundBlueCount = 2
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
  copyStatus.value = 'idle'
  errorMessage.value = ''
  infoMessage.value = ''
  try {
    const payload = JSON.parse(JSON.stringify(form))
    validateCompoundForm(payload)
    if (payload.seed === '' || payload.seed === null) {
      payload.seed = null
    } else {
      payload.seed = Number(payload.seed)
    }
    result.value = await api.generate(payload)
    activeView.value = 'results'
    await loadStatistics()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    generating.value = false
  }
}

function validateCompoundForm(payload) {
  if (!isCompoundMode(payload.betMode)) return
  if (payload.compoundRedCount === 6 && payload.compoundBlueCount === 1) {
    throw new Error('6+1 是单式，复式红球至少 7 个或蓝球至少 2 个')
  }
  const expandedTicketCount = combinations(payload.compoundRedCount, 6)
    * payload.compoundBlueCount
    * payload.ticketCount
  if (expandedTicketCount > 10000) {
    throw new Error(`当前配置将展开 ${expandedTicketCount} 注，最多允许 10000 注`)
  }
}

function isCompoundMode(mode) {
  return mode === 'COMPOUND' || mode === 'COMPOUND_7_2'
}

function combinations(total, choose) {
  const safeTotal = Number(total)
  if (!Number.isInteger(safeTotal) || choose < 0 || choose > safeTotal) return 0
  let value = 1
  for (let index = 1; index <= choose; index += 1) {
    value = value * (safeTotal - choose + index) / index
  }
  return Math.round(value)
}

function resultBetModeLabel(response) {
  if (!isCompoundMode(response?.betMode)) return betModeLabels[response?.betMode] || response?.betMode
  const firstGroup = response.compoundGroups?.[0]
  const redCount = response.compoundRedCount ?? firstGroup?.redBalls?.length ?? 7
  const blueCount = response.compoundBlueCount ?? firstGroup?.blueBalls?.length ?? 2
  return `${redCount}+${blueCount} 复式`
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

async function copyGeneratedNumbers() {
  const text = formatResultForCopy(result.value)
  if (!text) {
    errorMessage.value = '暂无可复制的号码'
    return
  }

  try {
    await writeClipboardText(text)
    copyStatus.value = 'copied'
  } catch (error) {
    copyStatus.value = 'failed'
    errorMessage.value = '复制失败，请检查浏览器剪贴板权限'
  }

  window.clearTimeout(copyStatusTimer)
  copyStatusTimer = window.setTimeout(() => {
    copyStatus.value = 'idle'
  }, 2000)
}

async function writeClipboardText(text) {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch (error) {
      // 浏览器拒绝 Clipboard API 时继续使用兼容方案
    }
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()

  try {
    if (!document.execCommand('copy')) {
      throw new Error('浏览器未执行复制命令')
    }
  } finally {
    textarea.remove()
  }
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

function formatBuildTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(value))
}

function percentage(value) {
  return `${(Number(value || 0) * 100).toFixed(1)}%`
}

function barWidth(count, max) {
  return `${Math.max(4, (count / max) * 100)}%`
}

function switchView(view) {
  if (view === 'results' && !result.value) return
  activeView.value = view
}

function trendCellLabel(row, cell) {
  const ballLabel = trendType.value === 'blue' ? '蓝球' : '红球'
  if (cell.hit) {
    return `${row.issue} ${ballLabel}第 ${cell.number} 号开出`
  }
  if (!showOmissions.value) {
    return `${row.issue} ${ballLabel}第 ${cell.number} 号未开出`
  }
  if (cell.omission === null) {
    return `${row.issue} ${ballLabel}第 ${cell.number} 号在窗口起点前状态未知`
  }
  return `${row.issue} ${ballLabel}第 ${cell.number} 号遗漏 ${cell.omission} 期`
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <button class="brand brand-button" type="button" aria-label="打开首页" @click="switchView('generator')">
        <span class="brand-mark"><i></i><i></i></span>
        <span>
          <strong>彩票预测-双色球</strong>
          <small>DOUBLE COLOR BALL</small>
        </span>
      </button>
      <nav class="desktop-nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.id"
          type="button"
          :class="{ active: activeView === item.id }"
          :aria-current="activeView === item.id ? 'page' : undefined"
          @click="switchView(item.id)"
        >
          {{ item.label }}
        </button>
      </nav>
      <label class="mobile-nav">
        <span>功能页面</span>
        <select :value="activeView" @change="switchView($event.target.value)">
          <option v-for="item in navItems" :key="item.id" :value="item.id">{{ item.label }}</option>
        </select>
      </label>
      <div
        v-if="historyStatus"
        class="data-state"
        :class="statusTone"
        :title="`前端构建时间：${formatBuildTime(appBuildTime)}`"
      >
        <span class="state-dot"></span>
        {{ historyStatus.recordCount }} 期 · 构建 {{ formatBuildTime(appBuildTime) }}
      </div>
    </header>

    <main class="workspace">
      <div v-if="errorMessage" class="toast error-toast" role="alert">
        <span>!</span>{{ errorMessage }}
        <button type="button" @click="errorMessage = ''">×</button>
      </div>
      <div v-if="infoMessage" class="toast info-toast" role="status">
        <span>✓</span>{{ infoMessage }}
        <button type="button" @click="infoMessage = ''">×</button>
      </div>

      <section v-show="activeView === 'generator'" class="screen section generator-section" aria-label="首页">
        <div class="section-heading">
          <div>
            <p class="eyebrow">01 / GENERATOR</p>
            <h2>配置组合条件</h2>
          </div>
          <p>所有范围均可调整。条件过窄时，后端会返回冲突原因，不会静默放宽约束</p>
        </div>

        <div class="workspace-tabs generator-tabs" role="tablist" aria-label="生成参数分组">
          <button type="button" :class="{ active: generatorPane === 'base' }" @click="generatorPane = 'base'">基础参数</button>
          <button type="button" :class="{ active: generatorPane === 'strategy' }" @click="generatorPane = 'strategy'">红球约束</button>
        </div>

        <form class="generator-grid" @submit.prevent="generate">
          <div class="panel base-panel" :class="{ 'pane-hidden': generatorPane !== 'base' }">
            <div class="panel-title">
              <span>基础参数</span>
              <small>OUTPUT & MATRIX</small>
            </div>
            <div class="field-grid" :class="{ 'compound-field-grid': compoundMode }">
              <label class="wide-field">
                <span>投注方式</span>
                <select v-model="form.betMode">
                  <option value="COMPOUND">复式</option>
                  <option value="STANDARD">单式</option>
                </select>
              </label>
              <div v-if="compoundMode" class="compound-spec wide-field">
                <label>
                  <span>红球数量</span>
                  <input v-model.number="form.compoundRedCount" type="number" min="6" max="33" />
                </label>
                <i>+</i>
                <label>
                  <span>蓝球数量</span>
                  <input v-model.number="form.compoundBlueCount" type="number" min="1" max="16" />
                </label>
                <div class="compound-spec-summary">
                  <strong>
                    每组 C({{ form.compoundRedCount }},6) × C({{ form.compoundBlueCount }},1)
                    = {{ compoundExpandedPerGroup }} 注 · {{ compoundExpandedPerGroup * 2 }} 元
                  </strong>
                  <small :class="{ warning: compoundConfigurationInvalid || compoundLimitExceeded }">
                    共 {{ compoundTotalExpanded }} 注
                    <template v-if="compoundConfigurationInvalid"> · 6+1 属于单式</template>
                    <template v-else-if="compoundLimitExceeded"> · 超过 10000 注上限</template>
                  </small>
                </div>
              </div>
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
              <label :class="{ 'wide-field': !matrixEnabled }">
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
              <label v-if="!compoundMode" class="wide-field">
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
            </div>
          </div>

          <div class="panel strategy-panel" :class="{ 'pane-hidden': generatorPane !== 'strategy' }">
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
              <label class="switch-row">
                <span><b>07</b> 过滤明显规律图案</span>
                <input v-model="form.strategy.avoidRegularPatterns" type="checkbox" />
                <i></i>
              </label>
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
            </div>
          </div>

          <button class="generate-button" type="submit" :disabled="generating || loading">
            <span v-if="generating" class="spinner"></span>
            <template v-else><i>→</i> 生成策略组合</template>
          </button>
        </form>
      </section>

      <section v-if="result" v-show="activeView === 'results'" class="screen section results-section" aria-label="生成结果">
        <div class="section-heading result-heading">
          <div>
            <p class="eyebrow">02 / GENERATED SET</p>
            <h2>本次组合结果</h2>
          </div>
          <div class="result-meta">
            <span>{{ resultBetModeLabel(result) }}</span>
            <span>种子 {{ result.seed }}</span>
            <span v-if="result.betMode === 'STANDARD'">{{ rotationLabels[result.rotationMode] }}</span>
            <span>{{ blueLabels[result.blueSelectionMode] }}</span>
            <button
              class="copy-result-button"
              :class="copyStatus"
              type="button"
              aria-live="polite"
              @click="copyGeneratedNumbers"
            >
              {{ copyStatus === 'copied' ? '已复制' : copyStatus === 'failed' ? '复制失败' : '复制号码' }}
            </button>
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
            <div v-if="isCompoundMode(result.betMode)"><dt>展开注数</dt><dd>{{ result.expandedTicketCount }}</dd></div>
            <div v-else><dt>二码覆盖</dt><dd>{{ percentage(result.coverage.pairCoverageRatio) }}</dd></div>
            <div v-if="isCompoundMode(result.betMode)"><dt>合计金额</dt><dd>{{ result.totalStakeAmountYuan }} 元</dd></div>
            <div v-else><dt>覆盖对数</dt><dd>{{ result.coverage.coveredPairCount }}/{{ result.coverage.possiblePairCount }}</dd></div>
          </dl>
        </div>
        <p class="coverage-note">
          {{ result.coverage.description }}
          · 池内三码 {{ result.coverage.coveredTripleCount }}/{{ result.coverage.possibleTripleCount }}
          · 蓝球覆盖 {{ result.coverage.uniqueBlueCount }}/16（{{ percentage(result.coverage.blueCoverageRatio) }}）
        </p>

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
                <span>{{ group.redBalls.length }}+{{ group.blueBalls.length }} 复式 · 第 {{ group.sequence }} 组</span>
                <small>每个六红子集均通过当前硬约束</small>
              </div>
              <b>结构分 {{ group.score }}</b>
            </header>

            <div class="compound-ball-groups">
              <section>
                <h3>红球 · {{ group.redBalls.length }} 选 6</h3>
                <div class="compound-balls compound-red">
                  <i v-for="number in group.redBalls" :key="number">{{ formatNumber(number) }}</i>
                </div>
              </section>
              <section>
                <h3>蓝球 · {{ group.blueBalls.length }} 选 1</h3>
                <div class="compound-balls compound-blue">
                  <i v-for="number in group.blueBalls" :key="number">{{ formatNumber(number) }}</i>
                </div>
              </section>
            </div>

            <div class="compound-summary" aria-live="polite">
              <div><strong>{{ group.expandedTicketCount }}</strong><span>注</span></div>
              <div><strong>{{ group.stakeAmountYuan }}</strong><span>元</span></div>
              <small>
                C({{ group.redBalls.length }},6) × C({{ group.blueBalls.length }},1)
                = {{ group.expandedTicketCount }} 注 · 每注 2 元
              </small>
            </div>

            <div class="highlight-list">
              <span v-for="highlight in group.highlights" :key="highlight">{{ highlight }}</span>
            </div>

            <button class="expanded-button" type="button" @click="expandedGroup = group">
              查看本组展开的 {{ group.expandedTicketCount }} 注单式
            </button>
          </article>
        </div>
        <p class="result-notice">{{ result.notice }}</p>
      </section>

      <section v-show="activeView === 'statistics'" class="screen section statistics-section" aria-label="历史统计">
        <div class="section-heading">
          <div>
            <p class="eyebrow">03 / HISTORY PROFILE</p>
            <h2>历史样本描述</h2>
          </div>
          <p v-if="statistics">最近 {{ statistics.lookback }} 期 · {{ statistics.notice }}</p>
        </div>

        <div class="workspace-tabs statistics-tabs" role="tablist" aria-label="统计分组">
          <button type="button" :class="{ active: statisticsPane === 'red' }" @click="statisticsPane = 'red'">红球频次</button>
          <button type="button" :class="{ active: statisticsPane === 'summary' }" @click="statisticsPane = 'summary'">蓝球与样本</button>
        </div>

        <div v-if="statistics" class="statistics-grid">
          <div class="panel frequency-panel" :class="{ 'pane-hidden': statisticsPane !== 'red' }">
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
          <div class="side-statistics" :class="{ 'pane-hidden': statisticsPane !== 'summary' }">
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

      <section v-show="activeView === 'trend'" class="screen section trend-section" aria-label="号码走势">
        <div class="section-heading">
          <div>
            <p class="eyebrow">04 / NUMBER TREND</p>
            <h2>红蓝球走势图</h2>
          </div>
          <p>点击红球或蓝球切换走势，实心球表示当期开奖，灰色数字表示连续遗漏期数</p>
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
            <small>{{ trendType === 'red' ? '红球 01–33' : '蓝球 01–16' }} · 当前显示 {{ trendRows.length }} 期</small>
          </div>

          <div v-if="trendRows.length" class="trend-view">
            <table class="trend-table" :class="`trend-${trendType}`">
              <thead>
                <tr>
                  <th class="trend-issue-col" scope="col">期号</th>
                  <th
                    v-for="number in trendNumbers"
                    :key="number"
                    :class="`trend-${trendType}-col`"
                    scope="col"
                  >
                    {{ formatNumber(number) }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in trendRows"
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
                    :class="[`trend-${trendType}-col`, { hit: cell.hit }]"
                    :aria-label="trendCellLabel(row, cell)"
                  >
                    <span v-if="cell.hit">{{ formatNumber(cell.number) }}</span>
                    <em v-else-if="showOmissions && cell.omission !== null">{{ cell.omission }}</em>
                    <em v-else-if="showOmissions">—</em>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="trend-empty">暂无可用于绘制走势的历史数据</p>
        </div>
      </section>

      <section v-show="activeView === 'history'" class="screen section history-section" aria-label="开奖数据">
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
              <tr v-for="draw in historyRows" :key="draw.issue">
                <td>{{ draw.issue }}</td>
                <td>{{ draw.drawDate }}</td>
                <td>
                  <span class="history-red-balls">
                    <i v-for="number in draw.redBalls" :key="number">{{ formatNumber(number) }}</i>
                  </span>
                </td>
                <td><i class="history-blue">{{ formatNumber(draw.blueBall) }}</i></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div v-if="expandedGroup" class="expanded-modal" role="dialog" aria-modal="true" :aria-label="`${expandedGroup.redBalls.length}+${expandedGroup.blueBalls.length} 展开单式`">
        <div class="expanded-modal-panel">
          <header>
            <div>
              <strong>第 {{ expandedGroup.sequence }} 组 · {{ expandedGroup.expandedTicketCount }} 注展开</strong>
              <small>
                {{ combinations(expandedGroup.redBalls.length, 6) }} 个六红子集
                × {{ expandedGroup.blueBalls.length }} 个蓝球
              </small>
            </div>
            <button type="button" aria-label="关闭展开明细" @click="expandedGroup = null">×</button>
          </header>
          <div class="expanded-ticket-grid">
            <div
              v-for="ticket in expandedGroup.expandedTickets"
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
        </div>
      </div>
    </main>
  </div>
</template>
