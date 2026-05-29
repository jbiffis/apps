// Client-side auth state. The JWT and the user record returned by
// POST /auth/login are kept in localStorage; logout just clears them.
// We never trust the token's contents for authorization (the server does
// that) — decoding is only for UI niceties (greeting name, expiry check).

const TOKEN_KEY = 'tracker.token'
const USER_KEY = 'tracker.user'
const MUST_CHANGE_KEY = 'tracker.mustChangePassword'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) : null
}

export function setSession(token, user, mustChangePassword = false) {
  localStorage.setItem(TOKEN_KEY, token)
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
  if (mustChangePassword) localStorage.setItem(MUST_CHANGE_KEY, '1')
  else localStorage.removeItem(MUST_CHANGE_KEY)
}

export function mustChangePassword() {
  return localStorage.getItem(MUST_CHANGE_KEY) === '1'
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(MUST_CHANGE_KEY)
}

// Decode the JWT payload without verifying the signature — UI only.
function decode(token) {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decodeURIComponent(escape(json)))
  } catch {
    return null
  }
}

export function isExpired(token = getToken()) {
  if (!token) return true
  const claims = decode(token)
  if (!claims?.exp) return true
  return claims.exp * 1000 <= Date.now()
}

export function isAuthenticated() {
  const token = getToken()
  return !!token && !isExpired(token)
}
