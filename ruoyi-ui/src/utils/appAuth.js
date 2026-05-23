const TokenKey = 'App-Token'

export function getAppToken() {
  return localStorage.getItem(TokenKey)
}

export function setAppToken(token) {
  return localStorage.setItem(TokenKey, token)
}

export function removeAppToken() {
  return localStorage.removeItem(TokenKey)
}
