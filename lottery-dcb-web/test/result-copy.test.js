import assert from 'node:assert/strict'
import test from 'node:test'
import { formatResultForCopy } from '../src/result-copy.js'

test('compound results copy configurable red and blue balls with blank lines', () => {
  const result = {
    betMode: 'COMPOUND',
    compoundGroups: [
      { redBalls: [3, 8, 14, 15, 23, 27, 30, 32], blueBalls: [1, 8, 12] },
      { redBalls: [7, 9, 13, 16, 22, 28, 33], blueBalls: [13, 15] }
    ]
  }

  assert.equal(
    formatResultForCopy(result),
    '3  8  14  15  23  27  30  32  +  1  8  12\n\n7  9  13  16  22  28  33  +  13  15'
  )
})

test('legacy seven plus two mode remains copy compatible', () => {
  const result = {
    betMode: 'COMPOUND_7_2',
    compoundGroups: [
      { redBalls: [3, 8, 14, 15, 23, 27, 32], blueBalls: [1, 8] }
    ]
  }

  assert.equal(formatResultForCopy(result), '3  8  14  15  23  27  32  +  1  8')
})

test('standard results use the same readable copy format', () => {
  const result = {
    betMode: 'STANDARD',
    tickets: [
      { redBalls: [1, 6, 12, 19, 25, 31], blueBall: 9 }
    ]
  }

  assert.equal(formatResultForCopy(result), '1  6  12  19  25  31  +  9')
})
