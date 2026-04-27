import { useEffect, useMemo, useState } from 'react'
import AppHeader from '../components/AppHeader'
import Icon from '../components/Icon'
import Knob from '../components/Knob'
import { useSongs } from '../hooks/useSongs'
import { vote, ApiError } from '../lib/api'
import type { Song } from '../lib/types'
import { getVoterId } from '../lib/voter'

type SortKey = 'votes' | 'title' | 'artist'

interface SortState {
  key: SortKey
  /** 'desc' is the natural default for votes; 'asc' for title/artist */
  dir: 'asc' | 'desc'
}

const DEFAULT_SORT: SortState = { key: 'votes', dir: 'desc' }

export default function Audience() {
  const voterId = useMemo(() => getVoterId(), [])
  const { songs, error, patch } = useSongs({ voter: voterId })
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<SortState>(DEFAULT_SORT)
  const [pending, setPending] = useState<Set<string>>(new Set())
  const [nudgeId, setNudgeId] = useState<string | null>(null)

  // Locally-cached "I voted" set so the UI is instant even if the API
  // takes a beat. The server is still source of truth.
  const [localVoted, setLocalVoted] = useState<Set<string>>(new Set())
  useEffect(() => {
    if (!songs) return
    setLocalVoted((prev) => {
      const next = new Set(prev)
      for (const s of songs) {
        if (s.voted) next.add(s.id)
      }
      return next
    })
  }, [songs])

  const list = songs ?? []

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    let out = q
      ? list.filter(
          (s) =>
            s.title.toLowerCase().includes(q) ||
            s.artist.toLowerCase().includes(q),
        )
      : list
    out = [...out].sort((a, b) => {
      const factor = sort.dir === 'asc' ? 1 : -1
      if (sort.key === 'votes') {
        if (a.votes === b.votes) {
          return a.title.localeCompare(b.title) * factor
        }
        return (a.votes - b.votes) * factor
      }
      if (sort.key === 'title') {
        return a.title.localeCompare(b.title) * factor
      }
      return a.artist.localeCompare(b.artist) * factor
    })
    return out
  }, [list, query, sort])

  function toggleSort(key: SortKey) {
    setSort((prev) => {
      if (prev.key !== key) {
        // First click on a column: title/artist → asc, votes → desc
        return { key, dir: key === 'votes' ? 'desc' : 'asc' }
      }
      return { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
    })
  }

  async function handleVote(song: Song) {
    if (localVoted.has(song.id) || pending.has(song.id)) return
    setLocalVoted((prev) => new Set(prev).add(song.id))
    setPending((prev) => new Set(prev).add(song.id))
    setNudgeId(song.id)
    setTimeout(() => setNudgeId((id) => (id === song.id ? null : id)), 400)

    // Optimistic vote count bump
    if (songs) {
      patch(
        songs.map((s) =>
          s.id === song.id ? { ...s, votes: s.votes + 1, voted: true } : s,
        ),
      )
    }

    try {
      const updated = await vote(song.id, voterId)
      if (songs) {
        patch(songs.map((s) => (s.id === song.id ? { ...updated } : s)))
      }
    } catch (e) {
      // Roll back on any error other than "already voted"
      const apiErr = e as ApiError
      if (apiErr.status !== 200) {
        if (songs) {
          patch(
            songs.map((s) =>
              s.id === song.id ? { ...s, votes: s.votes, voted: s.voted } : s,
            ),
          )
        }
        setLocalVoted((prev) => {
          const next = new Set(prev)
          next.delete(song.id)
          return next
        })
      }
    } finally {
      setPending((prev) => {
        const next = new Set(prev)
        next.delete(song.id)
        return next
      })
    }
  }

  return (
    <div style={{ minHeight: '100svh', maxWidth: 560, margin: '0 auto' }}>
      <AppHeader subtitle="Vote your favorites" />

      <main style={{ padding: '14px 16px 80px', display: 'grid', gap: 14 }}>
        {/* Search */}
        <div className="panel" style={{ padding: 12 }}>
          <div className="label-cap" style={{ marginBottom: 6 }}>
            Find a song
          </div>
          <div style={{ position: 'relative' }}>
            <input
              type="search"
              className="input"
              placeholder="Search title or artist…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              style={{ paddingLeft: 40 }}
            />
            <div
              style={{
                position: 'absolute',
                left: 12,
                top: '50%',
                transform: 'translateY(-50%)',
                pointerEvents: 'none',
                color: 'rgba(244,229,200,0.5)',
              }}
            >
              <Icon name="search" size={18} />
            </div>
          </div>
        </div>

        {/* Sort controls */}
        <div className="panel" style={{ padding: 10 }}>
          <div className="label-cap" style={{ marginBottom: 8, paddingLeft: 4 }}>
            Sort
          </div>
          <div style={{ display: 'flex', gap: 6 }}>
            {(
              [
                { key: 'votes', label: 'Votes' },
                { key: 'title', label: 'Title' },
                { key: 'artist', label: 'Artist' },
              ] as { key: SortKey; label: string }[]
            ).map((opt) => {
              const active = sort.key === opt.key
              return (
                <button
                  key={opt.key}
                  type="button"
                  onClick={() => toggleSort(opt.key)}
                  className="btn"
                  style={{
                    flex: 1,
                    justifyContent: 'center',
                    padding: '8px 10px',
                    background: active
                      ? 'linear-gradient(180deg, var(--orange-bright) 0%, var(--orange) 100%)'
                      : 'transparent',
                    color: active ? '#1a0f08' : 'var(--cream)',
                    borderColor: active ? 'var(--ink)' : 'var(--panel-edge)',
                    boxShadow: active
                      ? '0 3px 0 rgba(0,0,0,0.55)'
                      : 'none',
                    fontSize: 11,
                    gap: 4,
                  }}
                >
                  {opt.label}
                  {active && (
                    <Icon
                      name={sort.dir === 'asc' ? 'arrow-up' : 'arrow-down'}
                      size={12}
                    />
                  )}
                </button>
              )
            })}
          </div>
        </div>

        {/* Status / errors */}
        {error && (
          <div
            className="panel"
            style={{
              padding: 10,
              textAlign: 'center',
              fontSize: 12,
              fontWeight: 700,
              color: 'var(--orange-bright)',
            }}
          >
            {error}
          </div>
        )}

        {/* Songs */}
        <section style={{ display: 'grid', gap: 10 }}>
          {songs === undefined && <Skeleton />}
          {songs && filtered.length === 0 && songs.length === 0 && (
            <EmptyState
              title="No songs yet"
              desc="The musician hasn't loaded any songs. Check back in a sec!"
            />
          )}
          {songs && filtered.length === 0 && songs.length > 0 && (
            <EmptyState
              title="No matches"
              desc={`Nothing matches "${query}". Try a different search.`}
            />
          )}
          {filtered.map((s) => {
            const voted = localVoted.has(s.id) || s.voted === true
            const isPending = pending.has(s.id)
            // Knob rotation: -135 unvoted → +135 voted, with progress
            // toward +135 based on votes (capped). Simple visual nicety.
            const baseRot = voted ? 135 : -135
            return (
              <article
                key={s.id}
                className={`panel ${nudgeId === s.id ? 'nudge' : ''}`}
                style={{
                  padding: '10px 12px',
                  display: 'grid',
                  gridTemplateColumns: '1fr auto',
                  gap: 10,
                  alignItems: 'center',
                }}
              >
                <div style={{ minWidth: 0 }}>
                  <h3
                    className="serif"
                    style={{
                      fontSize: 16,
                      color: 'var(--cream)',
                      lineHeight: 1.2,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      marginBottom: 2,
                    }}
                  >
                    {s.title}
                  </h3>
                  <div
                    style={{
                      fontSize: 11,
                      fontWeight: 600,
                      color: 'rgba(244,229,200,0.5)',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      paddingLeft: 20,
                    }}
                  >
                    {s.artist}
                  </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                  <span
                    className="mono"
                    style={{
                      fontSize: 13,
                      fontWeight: 700,
                      color: voted ? 'var(--orange-bright)' : 'rgba(244,229,200,0.5)',
                      textShadow: voted ? '0 0 8px var(--orange-glow)' : 'none',
                      lineHeight: 1,
                    }}
                  >
                    {s.votes}
                  </span>
                  <Knob
                    size={52}
                    rotation={baseRot}
                    on={voted}
                    showTicks
                    label={voted ? 'VOTED' : 'VOTE'}
                    onClick={() => handleVote(s)}
                    disabled={voted || isPending}
                    aria-label={voted ? 'Already voted' : `Vote for ${s.title}`}
                  >
                    {voted ? <Icon name="check" size={15} stroke="#1a0f08" /> : null}
                  </Knob>
                </div>
              </article>
            )
          })}
        </section>

        {songs && filtered.length > 0 && (
          <p
            style={{
              fontSize: 11,
              color: 'rgba(244,229,200,0.45)',
              textAlign: 'center',
              fontWeight: 600,
              letterSpacing: '0.06em',
            }}
          >
            One vote per song · vote for as many as you like
          </p>
        )}
      </main>
    </div>
  )
}

function EmptyState({ title, desc }: { title: string; desc: string }) {
  return (
    <div
      className="panel"
      style={{
        padding: 28,
        textAlign: 'center',
        color: 'rgba(244,229,200,0.6)',
      }}
    >
      <div className="serif" style={{ fontSize: 22, color: 'var(--cream)' }}>
        {title}
      </div>
      <p style={{ marginTop: 6, fontSize: 13, fontWeight: 600 }}>{desc}</p>
    </div>
  )
}

function Skeleton() {
  return (
    <>
      {[0, 1, 2].map((i) => (
        <div key={i} className="panel" style={{ padding: 14, opacity: 0.5 }}>
          <div
            style={{
              height: 18,
              width: '70%',
              background: 'rgba(244,229,200,0.08)',
              borderRadius: 4,
              marginBottom: 8,
            }}
          />
          <div
            style={{
              height: 12,
              width: '40%',
              background: 'rgba(244,229,200,0.05)',
              borderRadius: 4,
            }}
          />
        </div>
      ))}
    </>
  )
}
