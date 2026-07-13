async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  })

  const contentType = response.headers.get('content-type') || ''
  const body = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    const message = typeof body === 'object' && body?.message
      ? body.message
      : `请求失败，HTTP ${response.status}`
    throw new Error(message)
  }
  return body
}

export const api = {
  getDefaults: () => request('/api/predictions/defaults'),
  generate: (payload) => request('/api/predictions/generate', {
    method: 'POST',
    body: JSON.stringify(payload)
  }),
  getHistory: (limit = 30) => request(`/api/history?limit=${limit}`),
  getHistoryStatus: () => request('/api/history/status'),
  refreshHistory: () => request('/api/history/refresh', { method: 'POST' }),
  getStatistics: (lookback = 100) => request(`/api/statistics?lookback=${lookback}`)
}
