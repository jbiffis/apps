import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchSongs } from '../lib/api'
import type { Song } from '../lib/types'

interface UseSongsOptions {
  voter?: string
  intervalMs?: number
}

export interface UseSongsResult {
  songs: Song[] | undefined
  error: string | null
  reload: () => Promise<void>
  /** Optimistic apply, will be reconciled on next reload. */
  patch: (songs: Song[]) => void
}

/**
 * Polling fetcher for the song list. Pauses when the document is hidden
 * to avoid background traffic during lock-screen sessions.
 */
export function useSongs({
  voter,
  intervalMs = 5000,
}: UseSongsOptions = {}): UseSongsResult {
  const [songs, setSongs] = useState<Song[] | undefined>()
  const [error, setError] = useState<string | null>(null)
  const stopRef = useRef(false)

  const load = useCallback(async () => {
    try {
      const next = await fetchSongs(voter)
      if (!stopRef.current) {
        setSongs(next)
        setError(null)
      }
    } catch (e) {
      if (!stopRef.current) {
        setError(e instanceof Error ? e.message : 'Could not load songs.')
      }
    }
  }, [voter])

  useEffect(() => {
    stopRef.current = false
    let timer: ReturnType<typeof setTimeout> | null = null

    const tick = async () => {
      if (document.hidden) {
        timer = setTimeout(tick, intervalMs)
        return
      }
      await load()
      if (!stopRef.current) timer = setTimeout(tick, intervalMs)
    }
    tick()

    const onVisible = () => {
      if (!document.hidden) load()
    }
    document.addEventListener('visibilitychange', onVisible)

    return () => {
      stopRef.current = true
      if (timer) clearTimeout(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [load, intervalMs])

  return { songs, error, reload: load, patch: setSongs }
}
