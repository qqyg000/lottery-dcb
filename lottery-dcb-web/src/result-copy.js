function formatBalls(numbers) {
  return (numbers || []).map(number => String(number)).join('  ')
}

export function formatResultForCopy(result) {
  if (!result) return ''

  const compoundMode = result.betMode === 'COMPOUND_7_2'
  const rows = compoundMode ? result.compoundGroups : result.tickets

  return (rows || []).map(row => {
    const blueBalls = compoundMode
      ? row.blueBalls
      : row.blueBall === null || row.blueBall === undefined
        ? []
        : [row.blueBall]
    return `${formatBalls(row.redBalls)}  +  ${formatBalls(blueBalls)}`
  }).join('\n\n')
}
