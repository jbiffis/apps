const KEY = 'dreamworld-admin'

export function getAdminPassword(): string {
  try {
    return sessionStorage.getItem(KEY) ?? ''
  } catch {
    return ''
  }
}

export function setAdminPassword(value: string): void {
  try {
    if (value) sessionStorage.setItem(KEY, value)
    else sessionStorage.removeItem(KEY)
  } catch {
    /* ignore */
  }
}

export function clearAdminPassword(): void {
  setAdminPassword('')
}
