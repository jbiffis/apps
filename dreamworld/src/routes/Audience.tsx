import { useEffect, useMemo, useState } from 'react'
import AppHeader from '../components/AppHeader'
import Icon from '../components/Icon'
import Knob from '../components/Knob'
import { useSongs } from '../hooks/useSongs'
import { vote } from '../lib/api'
import type { Song } from '../lib/types'
import { getVoterId } from '../lib/voter'

type SortKey = 'votes' | 'title' | 'artist'
interface SortState { key: SortKey; dir: 'asc' | 'desc' }
const DEFAULT_SORT: SortState = { key: 'votes', dir: 'desc' }

export default function Audience() {
  const voterId = useMemo(() => getVoterId(), [])
  const { songs, error, patch } = useSongs({ voter: voterId })
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<SortState>(DEFAULT_SORT)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [pending, setPending] = useState<Set<string>>(new Set())
  const [nudgeId, setNudgeId] = useState<string | null>(null)
  const [localVoted, setLocalVoted] = useState<Set<string>>(new Set())

  // Sync server-side voted state into localVoted
  useEffect(() => {
    if (!songs) return
    setLocalVoted((prev) => {
      const next = new Set(prev)
      for (const s of songs) {
        if (s.voted) next.add(s.id)
        else next.delete(s.id)
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
        if (a.votes === b.votes) return a.title.localeCompare(b.title) * factor
        return (a.votes - b.votes) * factor
      }
      if (sort.key === 'title') return a.title.localeCompare(b.title) * factor
      return a.artist.localeCompare(b.artist) * factor
    })
    return out
  }, [list, query, sort])

  function toggleSort(key: SortKey) {
    setSort((prev) => {
      if (prev.key !== key) return { key, dir: key === 'votes' ? 'desc' : 'asc' }
      return { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
    })
  }

  async function handleVote(song: Song) {
    if (pending.has(song.id)) return
    const wasVoted = localVoted.has(song.id)

    // Optimistic toggle
    setLocalVoted((prev) => {
      const next = new Set(prev)
      wasVoted ? next.delete(song.id) : next.add(song.id)
      return next
    })
    setPending((prev) => new Set(prev).add(song.id))
    if (!wasVoted) {
      setNudgeId(song.id)
      setTimeout(() => setNudgeId((id) => (id === song.id ? null : id)), 400)
    }
    if (songs) {
      patch(
        songs.map((s) =>
          s.id === song.id
            ? { ...s, votes: s.votes + (wasVoted ? -1 : 1), voted: !wasVoted }
            : s,
        ),
      )
    }

    try {
      const updated = await vote(song.id, voterId)
      if (songs) patch(songs.map((s) => (s.id === song.id ? { ...updated } : s)))
    } catch {
      // Roll back
      setLocalVoted((prev) => {
        const next = new Set(prev)
        wasVoted ? next.add(song.id) : next.delete(song.id)
        return next
      })
      if (songs) {
        patch(
          songs.map((s) =>
            s.id === song.id ? { ...s, votes: s.votes, voted: wasVoted } : s,
          ),
        )
      }
    } finally {
      setPending((prev) => {
        const next = new Set(prev)
        next.delete(song.id)
        return next
      })
    }
  }

  const activeFilters = query.trim() !== '' || sort.key !== 'votes' || sort.dir !== 'desc'

  return (
    <div style={{ minHeight: '100svh', maxWidth: 560, margin: '0 auto' }}>
      <AppHeader subtitle="Request your favorites" />

      <main style={{ padding: '10px 12px 72px', display: 'grid', gap: 8 }}>

        {/* Search + sort — collapsible on mobile */}
        <div className="panel" style={{ padding: '0' }}>
          {/* Toggle row */}
          <button
            type="button"
            onClick={() => setFiltersOpen((v) => !v)}
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '10px 14px',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--cream)',
              gap: 8,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Icon name="search" size={14} />
              <span
                style={{
                  fontFamily: 'Inter, sans-serif',
                  fontWeight: 700,
                  fontSize: 11,
                  letterSpacing: '0.12em',
                  textTransform: 'uppercase',
                  color: activeFilters ? 'var(--orange-bright)' : 'rgba(244,229,200,0.6)',
                }}
              >
                {query.trim() ? `"${query.trim()}"` : 'Search & Sort'}
              </span>
              {activeFilters && (
                <span
                  style={{
                    background: 'var(--orange)',
                    color: '#1a0d04',
                    borderRadius: 99,
                    fontSize: 9,
                    fontWeight: 800,
                    padding: '1px 6px',
                    letterSpacing: '0.04em',
                  }}
                >
                  ON
                </span>
              )}
            </div>
            <Icon
              name={filtersOpen ? 'arrow-up' : 'arrow-down'}
              size={14}
              stroke="rgba(244,229,200,0.5)"
            />
          </button>

          {filtersOpen && (
            <div
              style={{
                padding: '0 12px 12px',
                display: 'grid',
                gap: 8,
                borderTop: '1px solid rgba(255,165,50,0.1)',
              }}
            >
              {/* Search input */}
              <div style={{ position: 'relative', marginTop: 10 }}>
                <input
                  type="search"
                  className="input"
                  placeholder="Search title or artist…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  style={{ paddingLeft: 38, fontSize: 14, padding: '10px 14px 10px 38px' }}
                  autoFocus
                />
                <div
                  style={{
                    position: 'absolute',
                    left: 12,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    pointerEvents: 'none',
                    color: 'rgba(244,229,200,0.4)',
                  }}
                >
                  <Icon name="search" size={16} />
                </div>
              </div>

              {/* Sort row */}
              <div style={{ display: 'flex', gap: 5 }}>
                {(
                  [
                    { key: 'votes', label: 'Requests' },
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
                        padding: '7px 8px',
                        background: active
                          ? 'linear-gradient(180deg, var(--orange-bright) 0%, var(--orange) 100%)'
                          : 'transparent',
                        color: active ? '#1a0d04' : 'var(--cream)',
                        borderColor: active ? 'rgba(0,0,0,0.4)' : 'var(--panel-edge)',
                        boxShadow: active ? '0 2px 0 rgba(0,0,0,0.5)' : 'none',
                        fontSize: 11,
                        gap: 4,
                      }}
                    >
                      {opt.label}
                      {active && (
                        <Icon
                          name={sort.dir === 'asc' ? 'arrow-up' : 'arrow-down'}
                          size={11}
                        />
                      )}
                    </button>
                  )
                })}
              </div>
            </div>
          )}
        </div>

        {/* Errors */}
        {error && (
          <div
            className="panel"
            style={{
              padding: '8px 12px',
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
        <section style={{ display: 'grid', gap: 6 }}>
          {songs === undefined && <Skeleton />}
          {songs && filtered.length === 0 && songs.length === 0 && (
            <EmptyState
              title="No songs yet"
              desc="The musician hasn't loaded any songs yet."
            />
          )}
          {songs && filtered.length === 0 && songs.length > 0 && (
            <EmptyState
              title="No matches"
              desc={`Nothing matches "${query}".`}
            />
          )}
          {filtered.map((s) => {
            const voted = localVoted.has(s.id)
            const isPending = pending.has(s.id)
            return (
              <article
                key={s.id}
                className={`panel ${nudgeId === s.id ? 'nudge' : ''}`}
                style={{
                  padding: '9px 10px 9px 14px',
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
                      margin: 0,
                    }}
                  >
                    {s.title}
                  </h3>
                  <div
                    style={{
                      fontSize: 12,
                      fontWeight: 600,
                      color: 'rgba(244,229,200,0.5)',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      marginTop: 1,
                    }}
                  >
                    {s.artist}
                  </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3, flexShrink: 0 }}>
                  <span
                    className="mono"
                    style={{
                      fontSize: 12,
                      fontWeight: 700,
                      color: voted ? 'var(--orange-bright)' : 'rgba(244,229,200,0.45)',
                      textShadow: voted ? '0 0 8px var(--orange-glow)' : 'none',
                      lineHeight: 1,
                    }}
                  >
                    {s.votes}
                  </span>
                  <Knob
                    size={48}
                    rotation={voted ? 135 : -135}
                    on={voted}
                    showTicks
                    label={voted ? 'UNDO' : 'REQUEST'}
                    onClick={() => handleVote(s)}
                    disabled={isPending}
                    aria-label={voted ? `Remove request for ${s.title}` : `Request ${s.title}`}
                  >
                    {voted ? <Icon name="check" size={14} stroke="#1a0f08" /> : null}
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
              color: 'rgba(244,229,200,0.4)',
              textAlign: 'center',
              fontWeight: 600,
              letterSpacing: '0.05em',
              margin: 0,
            }}
          >
            Tap again to remove a request
          </p>
        )}
      </main>
    </div>
  )
}

function EmptyState({ title, desc }: { title: string; desc: string }) {
  return (
    <div className="panel" style={{ padding: 24, textAlign: 'center', color: 'rgba(244,229,200,0.6)' }}>
      <div className="serif" style={{ fontSize: 20, color: 'var(--cream)' }}>{title}</div>
      <p style={{ marginTop: 4, fontSize: 13, fontWeight: 600 }}>{desc}</p>
    </div>
  )
}

function Skeleton() {
  return (
    <>
      {[0, 1, 2, 3, 4].map((i) => (
        <div key={i} className="panel" style={{ padding: '9px 14px', opacity: 0.4 }}>
          <div style={{ height: 16, width: '65%', background: 'rgba(244,229,200,0.08)', borderRadius: 4, marginBottom: 6 }} />
          <div style={{ height: 12, width: '40%', background: 'rgba(244,229,200,0.05)', borderRadius: 4 }} />
        </div>
      ))}
    </>
  )
}
