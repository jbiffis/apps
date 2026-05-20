import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'

// Minimal GET hook: fetch on mount (and when `path` changes), expose
// {data, loading, error, reload}. Phase 1 keeps the data layer light; if
// caching/invalidation needs grow we can swap in TanStack Query behind this.
export function useApi(path, { enabled = true } = {}) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await api.get(path))
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [path])

  useEffect(() => {
    if (!enabled) return
    let alive = true
    api
      .get(path)
      .then((d) => alive && setData(d))
      .catch((e) => alive && setError(e))
      .finally(() => alive && setLoading(false))
    return () => {
      alive = false
    }
  }, [path, enabled])

  return { data, loading, error, reload: load }
}
