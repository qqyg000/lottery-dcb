import assert from 'node:assert/strict'
import test from 'node:test'

import { buildTrendRows } from '../src/trend.js'

const draws = [
  { issue: '003', redBalls: [1, 4], blueBall: 3 },
  { issue: '002', redBalls: [2, 4], blueBall: 1 },
  { issue: '001', redBalls: [1, 3], blueBall: 2 }
]

test('trend rows keep recent-first order and calculate window omissions', () => {
  const snapshot = structuredClone(draws)
  const rows = buildTrendRows(draws, 4, draw => draw.redBalls)

  assert.deepEqual(draws, snapshot)
  assert.deepEqual(rows.map(row => row.issue), ['003', '002', '001'])
  assert.equal(rows[0].cells[0].hit, true)
  assert.equal(rows[0].cells[1].omission, 1)
  assert.equal(rows[1].cells[0].omission, 1)
  assert.equal(rows[1].cells[1].hit, true)
  assert.equal(rows[2].cells[1].omission, null)
})

test('blue trend treats each draw as a single hit', () => {
  const rows = buildTrendRows(draws, 3, draw => [draw.blueBall])

  assert.equal(rows[0].cells[2].hit, true)
  assert.equal(rows[0].cells[0].omission, 1)
  assert.equal(rows[1].cells[0].hit, true)
  assert.equal(rows[2].cells[1].hit, true)
})

test('empty windows and numbers never hit do not invent omissions', () => {
  assert.deepEqual(buildTrendRows([], 3, draw => draw.redBalls), [])

  const rows = buildTrendRows(
    [{ issue: '002', redBalls: [1] }, { issue: '001', redBalls: [1] }],
    3,
    draw => draw.redBalls
  )
  assert.equal(rows[0].cells[0].hit, true)
  assert.equal(rows[1].cells[0].hit, true)
  assert.equal(rows[0].cells[2].omission, null)
})

test('omissions remain continuous across the full scrollable window', () => {
  const windowDraws = Array.from({ length: 12 }, (_, index) => {
    const issueNumber = 12 - index
    return {
      issue: String(issueNumber).padStart(3, '0'),
      redBalls: issueNumber === 6 ? [1] : [2]
    }
  })

  const fullWindow = buildTrendRows(windowDraws, 3, draw => draw.redBalls)
  const newestRows = fullWindow.slice(0, 6)

  assert.equal(newestRows[5].issue, '007')
  assert.equal(newestRows[5].cells[0].omission, 1)
  assert.equal(newestRows[0].cells[0].omission, 6)
})
