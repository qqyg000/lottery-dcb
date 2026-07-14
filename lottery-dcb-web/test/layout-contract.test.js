import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const styles = readFileSync(new URL('../src/styles.css', import.meta.url), 'utf8')
const indexHtml = readFileSync(new URL('../index.html', import.meta.url), 'utf8')
const favicon = readFileSync(new URL('../public/favicon.svg', import.meta.url), 'utf8')

test('navigation switches single-screen panels without anchor scrolling', () => {
  assert.match(app, /const activeView = ref\('generator'\)/)
  assert.match(app, /\{ id: 'generator', label: '首页' \}/)
  assert.match(app, /@click="switchView\('generator'\)"/)
  assert.match(app, /aria-current="activeView === item\.id \? 'page'/)
  assert.match(app, /v-show="activeView === 'generator'"/)
  assert.match(app, /v-show="activeView === 'history'"/)
  assert.doesNotMatch(app, /\{ id: 'home'/)
  assert.doesNotMatch(app, /\{ id: 'method'/)
  assert.doesNotMatch(app, /v-show="activeView === '(?:home|method)'"/)
  assert.doesNotMatch(app, /scrollIntoView/)
  assert.doesNotMatch(app, /href="#/)
})

test('browser and brand titles use the requested name', () => {
  assert.match(indexHtml, /<title>彩票预测-双色球<\/title>/)
  assert.match(app, /<strong>彩票预测-双色球<\/strong>/)
})

test('brand and browser icons use the same softer palette', () => {
  assert.match(indexHtml, /href="\/favicon\.svg\?v=2"/)
  assert.match(styles, /--brand-red:\s*#ed7b7c/)
  assert.match(styles, /--brand-blue:\s*#6f91d2/)
  assert.match(styles, /background:\s*var\(--brand-red\)/)
  assert.match(styles, /border:\s*6px solid var\(--brand-blue\)/)
  assert.match(favicon, /fill="#ed7b7c"/)
  assert.match(favicon, /fill="#6f91d2"/)
})

test('all records render without pagination controls or pagination state', () => {
  assert.match(app, /v-for="ticket in result\.tickets"/)
  assert.match(app, /v-for="group in result\.compoundGroups"/)
  assert.match(app, /v-for="draw in historyRows"/)
  assert.doesNotMatch(app, /screen-pagination|trend-pagination/)
  assert.doesNotMatch(app, /PageCount|PageSize|change\w*Page|paginate\(/)
  assert.match(app, /generatorPane/)
  assert.match(app, /statisticsPane/)
})

test('compound generation defaults to two groups and exposes result copying', () => {
  assert.match(app, /ticketCount:\s*2/)
  assert.equal((app.match(/form\.ticketCount = 2/g) || []).length, 2)
  assert.match(app, /class="copy-result-button"/)
  assert.match(app, /@click="copyGeneratedNumbers"/)
  assert.match(styles, /\.compound-grid\s*\{[^}]*padding-right:\s*0[^}]*scrollbar-gutter:\s*auto/s)
})

test('compound results stay in two columns and scroll downward after four cards', () => {
  assert.match(styles, /\.results-section\s*\{[^}]*padding-block:\s*clamp\(10px,\s*1\.4vh,\s*14px\)/s)
  assert.match(styles, /\.compound-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)[^}]*grid-auto-flow:\s*row[^}]*overflow-x:\s*hidden[^}]*overflow-y:\s*auto/s)
  assert.match(styles, /@media\s*\(max-width:\s*1080px\)\s*\{[\s\S]*?\.compound-grid\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/s)
  assert.match(styles, /\.compound-card\s*\{[^}]*padding:\s*clamp\(12px,\s*1\.6vh,\s*16px\)/s)
  assert.match(styles, /\.compound-ball-groups\s*\{[^}]*padding:\s*clamp\(10px,\s*1\.7vh,\s*16px\) 0/s)
})

test('root stays fixed while dense content scrolls inside the active screen', () => {
  assert.match(styles, /height:\s*100dvh/)
  assert.match(styles, /grid-template-rows:\s*auto minmax\(0, 1fr\)/)
  assert.match(styles, /\.workspace\s*\{[^}]*height:\s*100%[^}]*overflow:\s*hidden/s)
  assert.match(styles, /html,\s*body,\s*#app\s*\{[^}]*overflow:\s*hidden/s)
  assert.match(styles, /\.screen\s*\{[^}]*overflow:\s*auto/s)
  assert.match(styles, /\.trend-view\s*\{[^}]*overflow:\s*auto/s)
  assert.match(styles, /\.history-table-wrap\s*\{[^}]*overflow:\s*auto/s)
  assert.doesNotMatch(styles, /pagination/)
})

test('responsive workbench uses available panel space', () => {
  assert.match(app, /:class="\{ 'wide-field': !matrixEnabled \}"/)
  assert.match(styles, /width:\s*min\(1600px, calc\(100% - 48px\)\)/)
  assert.match(styles, /grid-template-rows:\s*repeat\(6, minmax\(54px, 1fr\)\)/)
  assert.match(styles, /grid-template-rows:\s*repeat\(5, minmax\(56px, 1fr\)\)/)
  assert.match(styles, /\.generator-grid > \.panel\s*\{[^}]*display:\s*flex[^}]*flex-direction:\s*column/s)
})

test('statistics panels distribute rows across available height', () => {
  assert.match(styles, /\.frequency-panel,\s*\.compact-panel\s*\{[^}]*display:\s*flex[^}]*flex-direction:\s*column/s)
  assert.match(styles, /\.side-statistics\s*\{[^}]*grid-template-rows:\s*minmax\(0, 0\.8fr\) minmax\(0, 1\.2fr\)/s)
  assert.match(styles, /\.red-frequency\s*\{[^}]*grid-auto-rows:\s*minmax\(34px, 1fr\)/s)
  assert.match(styles, /\.blue-frequency\s*\{[^}]*grid-auto-rows:\s*minmax\(32px, 1fr\)/s)
  assert.match(styles, /\.sample-groups\s*\{[^}]*grid-template-rows:\s*repeat\(2, minmax\(0, 1fr\)\)/s)
})

test('trend and history data use readable type and ball sizes', () => {
  assert.match(app, /点击红球或蓝球切换走势，实心球表示当期开奖，灰色数字表示连续遗漏期数/)
  assert.match(app, /const trendType = ref\('red'\)/)
  assert.match(app, /class="trend-tabs"/)
  assert.doesNotMatch(app, /trend-ranges|trendRangeIndex|activeTrendRange/)
  assert.match(app, /v-for="number in trendNumbers"/)
  assert.match(app, /v-for="row in trendRows"/)
  assert.match(styles, /\.trend-table th,\s*\.trend-table td\s*\{[^}]*height:\s*42px/s)
  assert.match(styles, /\.trend-table thead th\s*\{[^}]*height:\s*33px[^}]*font:\s*600 12px\/1\.2/s)
  assert.match(styles, /\.trend-cell span\s*\{[^}]*width:\s*min\(32px[^}]*font:\s*600 12px\/1/s)
  assert.match(styles, /\.trend-cell em\s*\{[^}]*font:\s*normal 11px\/1/s)
  assert.match(styles, /\.history-table th\s*\{[^}]*height:\s*36px[^}]*font-size:\s*12px/s)
  assert.match(styles, /\.history-table td\s*\{[^}]*height:\s*43px[^}]*font-size:\s*14px/s)
  assert.match(styles, /\.status-grid strong\.success\s*\{[^}]*font-weight:\s*700/s)
  assert.match(styles, /\.history-table i\s*\{[^}]*width:\s*32px[^}]*height:\s*32px[^}]*background:\s*var\(--red\)[^}]*color:\s*#fff[^}]*font:\s*600 12px\/1 var\(--mono\)[^}]*box-shadow:\s*0 2px 5px rgba\(185, 31, 45, 0\.18\)/s)
  assert.match(styles, /\.history-table \.history-blue\s*\{[^}]*background:\s*var\(--blue\)[^}]*color:\s*#fff[^}]*box-shadow:\s*0 2px 5px rgba\(36, 92, 199, 0\.2\)/s)
  assert.match(styles, /\.trend-table\.trend-red\s*\{[^}]*min-width:\s*1320px/s)
  assert.match(styles, /\.trend-table\.trend-blue\s*\{[^}]*min-width:\s*800px/s)
  assert.match(styles, /\.history-table\s*\{[^}]*min-width:\s*680px/s)
})

test('history draw columns are balanced and red balls use explicit spacing', () => {
  assert.match(app, /class="history-red-balls"/)
  assert.match(styles, /\.history-table th:first-child,\s*\.history-table td:first-child\s*\{[^}]*width:\s*18%/s)
  assert.match(styles, /\.history-table th:nth-child\(2\),\s*\.history-table td:nth-child\(2\)\s*\{[^}]*width:\s*20%/s)
  assert.match(styles, /\.history-table th:nth-child\(3\),\s*\.history-table td:nth-child\(3\)\s*\{[^}]*width:\s*44%/s)
  assert.match(styles, /\.history-table th:last-child,\s*\.history-table td:last-child\s*\{[^}]*width:\s*18%/s)
  assert.match(styles, /\.history-table th,\s*\.history-table td\s*\{[^}]*text-align:\s*center/s)
  assert.match(styles, /\.history-red-balls\s*\{[^}]*display:\s*inline-flex[^}]*gap:\s*10px/s)
})
