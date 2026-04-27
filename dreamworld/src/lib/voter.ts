const KEY = 'dreamworld-voter'

function generateUuid(): string {
  const c = typeof crypto !== 'undefined' ? crypto : undefined
  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID().replace(/-/g, '')
  }
  const bytes = new Uint8Array(16)
  if (c && typeof c.getRandomValues === 'function') {
    c.getRandomValues(bytes)
  } else {
    for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256)
  }
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

/** Returns a stable per-device voter UUID, creating one on first call. */
export function getVoterId(): string {
  try {
    let id = localStorage.getItem(KEY)
    if (!id) {
      id = generateUuid()
      localStorage.setItem(KEY, id)
    }
    return id
  } catch {
    // localStorage may be blocked. Fall back to a session-only id; the
    // caller will get duplicates across reloads but vote will still work.
    return generateUuid()
  }
}
