// JWT-aware fetch client. Every call is base-relative so it works identically
// in dev (Vite proxy strips /tracker) and prod (Apache proxies /tracker/api
// to the backend). The Bearer token is attached automatically; a 401 clears
// the session and bubbles up so the router can redirect to login.

import { getToken, clearSession } from './auth.js'

const BASE = `${import.meta.env.BASE_URL}api` // '/tracker/api'

export class ApiError extends Error {
  constructor(status, code, message, body) {
    super(message || code || `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.body = body
  }
}

async function request(method, path, { body, auth = true, signal } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) {
    const token = getToken()
    if (token) headers['Authorization'] = `Bearer ${token}`
  }

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal,
  })

  if (res.status === 401) {
    clearSession()
  }

  const text = await res.text()
  const data = text ? safeJson(text) : null

  if (!res.ok) {
    throw new ApiError(res.status, data?.error, data?.message, data)
  }
  return data
}

function safeJson(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export const api = {
  get: (path, opts) => request('GET', path, opts),
  post: (path, body, opts) => request('POST', path, { ...opts, body }),
  put: (path, body, opts) => request('PUT', path, { ...opts, body }),
  del: (path, opts) => request('DELETE', path, opts),
}

// Convenience wrappers used across screens (Epics 6+ build on these).
export const auth = {
  login: (username, password) =>
    api.post('/auth/login', { username, password }, { auth: false }),
  me: () => api.get('/auth/me'),
}
