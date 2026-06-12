export function buildEventSourceUrl(path, params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      query.set(key, String(value))
    }
  })

  const queryString = query.toString()
  return `/dev-api${path}${queryString ? `?${queryString}` : ''}`
}

export function closeEventSource(sourceRef) {
  if (sourceRef?.value) {
    sourceRef.value.close()
    sourceRef.value = null
  }
}
