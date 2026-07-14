import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const styles = readFileSync(new URL('../src/styles.css', import.meta.url), 'utf8')

test('navigation switches single-screen panels without anchor scrolling', () => {
  assert.match(app, /const activeView = ref\('home'\)/)
  assert.match(app, /aria-current="activeView === item\.id \? 'page'/)
  assert.match(app, /v-show="activeView === 'generator'"/)
  assert.match(app, /v-show="activeView === 'history'"/)
  assert.doesNotMatch(app, /scrollIntoView/)
  assert.doesNotMatch(app, /href="#/)
})

test('all records render without pagination controls or pagination state', () => {
  assert.match(app, /v-for="ticket in result\.tickets"/)
  assert.match(app, /v-for="group in result\.compoundGroups"/)
  assert.match(app, /v-for="draw in historyRows"/)
  assert.match(app, /v-for="strategy in strategyCards"/)
  assert.doesNotMatch(app, /screen-pagination|trend-pagination/)
  assert.doesNotMatch(app, /PageCount|PageSize|change\w*Page|paginate\(/)
  assert.match(app, /generatorPane/)
  assert.match(app, /statisticsPane/)
})

test('root stays fixed while dense content scrolls inside the active screen', () => {
  assert.match(styles, /height:\s*100dvh/)
  assert.match(styles, /grid-template-rows:\s*auto minmax\(0, 1fr\)/)
  assert.match(styles, /\.workspace\s*\{[^}]*height:\s*100%[^}]*overflow:\s*hidden/s)
  assert.match(styles, /html,\s*body,\s*#app\s*\{[^}]*overflow:\s*hidden/s)
  assert.match(styles, /\.screen\s*\{[^}]*overflow:\s*auto/s)
  assert.match(styles, /\.trend-view\s*\{[^}]*overflow:\s*auto/s)
  assert.match(styles, /\.history-table-wrap\s*\{[^}]*overflow:\s*auto/s)
  assert.match(styles, /\.method-grid\s*\{[^}]*overflow:\s*auto/s)
  assert.doesNotMatch(styles, /pagination/)
})
