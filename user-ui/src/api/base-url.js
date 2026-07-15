export const API_BASE_URL = import.meta.env.VITE_APP_BASE_API || '/dev-api'

export function apiUrl(path) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE_URL}${normalizedPath}`
}
