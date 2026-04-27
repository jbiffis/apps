import type { Song } from './types'
import { getAdminPassword } from './auth'

const BASE = '/dreamworld/api'

export class ApiError extends Error {
  status: number
  code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function parseError(res: Response): Promise<ApiError> {
  let code = 'request_failed'
  let message = res.statusText
  try {
    const body = await res.json()
    code = body.error ?? code
    message = body.message || code
  } catch {
    /* ignore */
  }
  return new ApiError(res.status, code, message)
}

export async function fetchSongs(voter?: string): Promise<Song[]> {
  const url = new URL(`${BASE}/songs.php`, window.location.origin)
  if (voter) url.searchParams.set('voter', voter)
  const res = await fetch(url.toString(), {
    headers: { Accept: 'application/json' },
    cache: 'no-store',
  })
  if (!res.ok) throw await parseError(res)
  const data = await res.json()
  return Array.isArray(data.songs) ? data.songs : []
}

export async function addSong(title: string, artist: string): Promise<Song> {
  const res = await fetch(`${BASE}/songs.php`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Admin-Password': getAdminPassword(),
    },
    body: JSON.stringify({ title, artist }),
  })
  if (!res.ok) throw await parseError(res)
  const data = await res.json()
  return data.song
}

export async function updateSong(id: string, title: string, artist: string): Promise<Song> {
  const res = await fetch(`${BASE}/songs.php`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-Admin-Password': getAdminPassword(),
    },
    body: JSON.stringify({ id, title, artist }),
  })
  if (!res.ok) throw await parseError(res)
  const data = await res.json()
  return data.song
}

export async function deleteSong(id: string): Promise<void> {
  const url = new URL(`${BASE}/songs.php`, window.location.origin)
  url.searchParams.set('id', id)
  const res = await fetch(url.toString(), {
    method: 'DELETE',
    headers: { 'X-Admin-Password': getAdminPassword() },
  })
  if (!res.ok) throw await parseError(res)
}

export async function vote(songId: string, voter: string): Promise<Song> {
  const res = await fetch(`${BASE}/vote.php`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ songId, voter }),
  })
  if (!res.ok) throw await parseError(res)
  const data = await res.json()
  return data.song
}

export async function resetVotes(): Promise<number> {
  const res = await fetch(`${BASE}/reset.php`, {
    method: 'POST',
    headers: { 'X-Admin-Password': getAdminPassword() },
  })
  if (!res.ok) throw await parseError(res)
  const data = await res.json()
  return data.count ?? 0
}
