import { useCallback, useEffect, useState } from 'react'

const STORAGE_KEY = 'booklog-profile'
const UPDATE_EVENT = 'booklog-profile-update'

export interface Profile {
  name: string
  avatar: string
  createdAt: number
  onboarded: boolean
}

export const AVATAR_CHOICES = [
  '🦊',
  '🐻',
  '🐼',
  '🐨',
  '🦁',
  '🐯',
  '🐸',
  '🐙',
  '🦄',
  '🐧',
  '🦉',
  '🐢',
  '🐶',
  '🐱',
  '🐰',
  '🦋',
  '🦖',
  '🐉',
  '🌮',
  '🍕',
  '🍪',
  '🍩',
  '🍦',
  '🍓',
] as const

export function defaultProfile(): Profile {
  return {
    name: '',
    avatar: '🦊',
    createdAt: Date.now(),
    onboarded: false,
  }
}

function read(): Profile | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<Profile>
    if (typeof parsed.name !== 'string') return null
    return { ...defaultProfile(), ...parsed }
  } catch {
    return null
  }
}

function write(p: Profile): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(p))
  window.dispatchEvent(new Event(UPDATE_EVENT))
}

export function useProfile(): {
  profile: Profile | null
  update: (changes: Partial<Profile>) => void
  reset: () => void
} {
  const [profile, setProfile] = useState<Profile | null>(() =>
    typeof window === 'undefined' ? null : read(),
  )

  useEffect(() => {
    const sync = () => setProfile(read())
    window.addEventListener('storage', sync)
    window.addEventListener(UPDATE_EVENT, sync)
    return () => {
      window.removeEventListener('storage', sync)
      window.removeEventListener(UPDATE_EVENT, sync)
    }
  }, [])

  const update = useCallback((changes: Partial<Profile>) => {
    const current = read() ?? defaultProfile()
    const next: Profile = { ...current, ...changes }
    write(next)
  }, [])

  const reset = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    window.dispatchEvent(new Event(UPDATE_EVENT))
  }, [])

  return { profile, update, reset }
}
