export function buildTrendRows(recentFirst, maxNumber, selectNumbers) {
  const misses = Array(maxNumber + 1).fill(0)
  const known = Array(maxNumber + 1).fill(false)
  const chronological = [...recentFirst].reverse()
  const rows = chronological.map(draw => {
    const hits = new Set(selectNumbers(draw).map(Number))
    const cells = Array.from({ length: maxNumber }, (_, index) => {
      const number = index + 1
      if (hits.has(number)) {
        known[number] = true
        misses[number] = 0
        return { number, hit: true, omission: null }
      }
      if (!known[number]) {
        return { number, hit: false, omission: null }
      }
      misses[number] += 1
      return { number, hit: false, omission: misses[number] }
    })
    return { ...draw, cells }
  })
  return rows.reverse()
}
