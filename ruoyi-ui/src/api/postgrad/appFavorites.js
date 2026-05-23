import request from '@/utils/appRequest'

export function listFavorites() {
  return request({ url: '/app/favorites', method: 'get' })
}

export function addFavorite(programId) {
  return request({ url: '/app/favorites/' + programId, method: 'post' })
}

export function removeFavorite(programId) {
  return request({ url: '/app/favorites/' + programId, method: 'delete' })
}
