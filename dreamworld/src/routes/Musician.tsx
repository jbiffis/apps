import { useMemo, useRef, useState } from 'react'
import AddSongForm from '../components/AddSongForm'
import AppHeader from '../components/AppHeader'
import Icon from '../components/Icon'
import PasswordGate from '../components/PasswordGate'
import QrModal from '../components/QrModal'
import VuMeter from '../components/VuMeter'
import { useSongs } from '../hooks/useSongs'
import { addSong, deleteSong, resetVotes, updateSong } from '../lib/api'
import { clearAdminPassword, getAdminPassword } from '../lib/auth'

type SortKey = 'votes' | 'title' | 'artist'
interface SortState { key: SortKey; dir: 'asc' | 'desc' }
const DEFAULT_SORT: SortState = { key: 'votes', dir: 'desc' }

export default function Musician() {
  const [unlocked, setUnlocked] = useState(() => Boolean(getAdminPassword()))
  if (!unlocked) {
    return (
      <PasswordGate onUnlock={() => setUnlocked(true)}>
        <Stage onLogout={() => setUnlocked(false)} />
      </PasswordGate>
    )
  }
  return <Stage onLogout={() => setUnlocked(false)} />
}

function Stage({ onLogout }: { onLogout: () => void }) {
  const { songs, error, reload, patch } = useSongs()
  const [qrOpen, setQrOpen] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [resetMsg, setResetMsg] = useState('')
  const [sort, setSort] = useState<SortState>(DEFAULT_SORT)
  const [randomId, setRandomId] = useState<string | null>(null)
  const rowRefs = useRef<Record<string, HTMLDivElement | null>>({})

  const voteUrl = `${window.location.origin}/dreamworld/vote`
  const list = songs ?? []
  const maxVotes = useMemo(
    () => list.reduce((m, s) => Math.max(m, s.votes), 0),
    [list],
  )
  const totalVotes = useMemo(
    () => list.reduce((sum, s) => sum + s.votes, 0),
    [list],
  )

  const sorted = useMemo(() => {
    const factor = sort.dir === 'asc' ? 1 : -1
    return [...list].sort((a, b) => {
      if (sort.key === 'votes') {
        if (a.votes === b.votes) return a.title.localeCompare(b.title)
        return (a.votes - b.votes) * factor
      }
      if (sort.key === 'title') return a.title.localeCompare(b.title) * factor
      return a.artist.localeCompare(b.artist) * factor
    })
  }, [list, sort])

  const randomSong = useMemo(
    () => list.find((s) => s.id === randomId) ?? null,
    [list, randomId],
  )

  function toggleSort(key: SortKey) {
    setSort((prev) => {
      if (prev.key !== key) return { key, dir: key === 'votes' ? 'desc' : 'asc' }
      return { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
    })
  }

  function handleRandom() {
    if (list.length === 0) return
    const pick = list[Math.floor(Math.random() * list.length)]
    setRandomId(pick.id)
    // Scroll the chosen row into view once it has been highlighted.
    requestAnimationFrame(() => {
      rowRefs.current[pick.id]?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    })
  }

  async function handleAdd(title: string, artist: string) {
    const created = await addSong(title, artist)
    // Optimistic insert; reload reconciles in 5s.
    if (songs) {
      patch(
        [...songs, { ...created, voted: false }].sort((a, b) => {
          if (a.votes !== b.votes) return b.votes - a.votes
          return a.addedAt - b.addedAt
        }),
      )
    }
    reload()
  }

  async function handleDelete(id: string, title: string) {
    if (!confirm(`Remove "${title}" from the setlist?`)) return
    try {
      await deleteSong(id)
      if (songs) patch(songs.filter((s) => s.id !== id))
      reload()
    } catch {
      reload()
    }
  }

  async function handleReset() {
    if (
      !confirm(
        'Reset all requests to zero? Use this between sets. Songs stay on the list.',
      )
    )
      return
    setResetting(true)
    setResetMsg('')
    try {
      const count = await resetVotes()
      setResetMsg(`Cleared requests on ${count} ${count === 1 ? 'song' : 'songs'}.`)
      if (songs) patch(songs.map((s) => ({ ...s, votes: 0, voted: false })))
      reload()
      setTimeout(() => setResetMsg(''), 3500)
    } catch {
      setResetMsg('Could not reset requests.')
    } finally {
      setResetting(false)
    }
  }

  function handleLogout() {
    clearAdminPassword()
    onLogout()
  }

  return (
    <div style={{ minHeight: '100svh', maxWidth: 720, margin: '0 auto' }}>
      <AppHeader
        subtitle="Stage console"
        rightSlot={
          <button
            type="button"
            onClick={handleLogout}
            className="btn btn-ghost"
            style={{ padding: '6px 10px', fontSize: 10 }}
          >
            <Icon name="logout" size={12} /> Sign out
          </button>
        }
      />

      <main style={{ padding: '16px 16px 80px', display: 'grid', gap: 16 }}>
        {/* Stats strip */}
        <div
          className="panel"
          style={{
            padding: 14,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
          }}
        >
          <Stat label="Setlist" value={list.length} />
          <Stat label="Requests" value={totalVotes} accent />
          <Stat label="Top" value={maxVotes} accent />
        </div>

        <AddSongForm onAdd={handleAdd} />

        {/* QR + reset */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <button
            type="button"
            onClick={() => setQrOpen(true)}
            className="btn btn-primary"
            style={{ justifyContent: 'center', padding: '14px 16px' }}
          >
            <Icon name="qr" size={16} /> Show QR
          </button>
          <button
            type="button"
            onClick={handleReset}
            disabled={resetting || list.length === 0}
            className="btn"
            style={{ justifyContent: 'center', padding: '14px 16px' }}
          >
            <Icon name="reset" size={16} /> {resetting ? 'Resetting…' : 'Reset requests'}
          </button>
        </div>

        {/* Random pick */}
        <button
          type="button"
          onClick={handleRandom}
          disabled={list.length === 0}
          className="btn"
          style={{ justifyContent: 'center', padding: '14px 16px' }}
        >
          <Icon name="shuffle" size={16} /> Pick a random song
        </button>
        {randomSong && (
          <div
            className="panel"
            style={{
              padding: '12px 14px',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              borderColor: 'var(--orange)',
              boxShadow: '0 0 14px var(--orange-glow)',
            }}
          >
            <Icon name="shuffle" size={18} stroke="var(--orange-bright)" />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="label-cap" style={{ color: 'var(--orange-bright)' }}>
                Random pick
              </div>
              <div
                className="serif"
                style={{
                  fontSize: 18,
                  color: 'var(--cream)',
                  lineHeight: 1.15,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {randomSong.title}
              </div>
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: 'rgba(244,229,200,0.55)',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {randomSong.artist}
              </div>
            </div>
            <button
              type="button"
              onClick={() => setRandomId(null)}
              className="btn btn-ghost"
              style={{ padding: '6px 8px', flexShrink: 0 }}
              aria-label="Clear random pick"
              title="Clear"
            >
              <Icon name="close" size={14} />
            </button>
          </div>
        )}
        {resetMsg && (
          <div
            className="panel"
            style={{
              padding: 10,
              fontSize: 12,
              fontWeight: 700,
              textAlign: 'center',
              color: 'var(--orange-bright)',
              letterSpacing: '0.04em',
            }}
          >
            {resetMsg}
          </div>
        )}

        {/* Setlist */}
        <section style={{ display: 'grid', gap: 8 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'baseline',
              justifyContent: 'space-between',
            }}
          >
            <span className="label-cap">Setlist</span>
            {error && (
              <span
                style={{ fontSize: 11, color: 'var(--orange-bright)', fontWeight: 700 }}
              >
                {error}
              </span>
            )}
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

          {songs === undefined && <SkeletonRows />}

          {songs && songs.length === 0 && <EmptySetlist />}

          {songs &&
            sorted.map((s) => (
              <SongRow
                key={s.id}
                id={s.id}
                title={s.title}
                artist={s.artist}
                votes={s.votes}
                maxVotes={maxVotes || 1}
                highlight={s.id === randomId}
                setRef={(el) => { rowRefs.current[s.id] = el }}
                onDelete={() => handleDelete(s.id, s.title)}
                onEdit={async (title, artist) => {
                  const updated = await updateSong(s.id, title, artist)
                  if (songs) patch(songs.map((x) => (x.id === s.id ? { ...x, ...updated } : x)))
                }}
              />
            ))}
        </section>
      </main>

      {qrOpen && <QrModal url={voteUrl} onClose={() => setQrOpen(false)} />}
    </div>
  )
}

function Stat({
  label,
  value,
  accent = false,
}: {
  label: string
  value: number
  accent?: boolean
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, flex: 1 }}>
      <span className="dial-label" style={{ fontSize: 9 }}>
        {label}
      </span>
      <span
        className="serif"
        style={{
          fontSize: 24,
          color: accent ? 'var(--orange-bright)' : 'var(--cream)',
          textShadow: accent ? '0 0 10px var(--orange-glow)' : 'none',
          lineHeight: 1,
        }}
      >
        {value}
      </span>
    </div>
  )
}

function SongRow({
  id: _id,
  title,
  artist,
  votes,
  maxVotes,
  highlight = false,
  setRef,
  onDelete,
  onEdit,
}: {
  id: string
  title: string
  artist: string
  votes: number
  maxVotes: number
  highlight?: boolean
  setRef?: (el: HTMLDivElement | null) => void
  onDelete: () => void
  onEdit: (title: string, artist: string) => Promise<void>
}) {
  const [editing, setEditing] = useState(false)
  const [editTitle, setEditTitle] = useState(title)
  const [editArtist, setEditArtist] = useState(artist)
  const [saving, setSaving] = useState(false)

  async function handleSave() {
    const t = editTitle.trim()
    const a = editArtist.trim()
    if (!t || !a) return
    setSaving(true)
    try {
      await onEdit(t, a)
      setEditing(false)
    } finally {
      setSaving(false)
    }
  }

  function handleCancel() {
    setEditTitle(title)
    setEditArtist(artist)
    setEditing(false)
  }

  if (editing) {
    return (
      <div className="panel" style={{ padding: 14, display: 'grid', gap: 8 }}>
        <input
          className="input"
          value={editTitle}
          onChange={(e) => setEditTitle(e.target.value)}
          placeholder="Title"
          style={{ fontSize: 14, padding: '9px 12px' }}
          autoFocus
        />
        <input
          className="input"
          value={editArtist}
          onChange={(e) => setEditArtist(e.target.value)}
          placeholder="Artist"
          style={{ fontSize: 14, padding: '9px 12px' }}
        />
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving || !editTitle.trim() || !editArtist.trim()}
            className="btn btn-primary"
            style={{ flex: 1, justifyContent: 'center', padding: '10px 14px' }}
          >
            {saving ? 'Saving…' : 'Save'}
          </button>
          <button
            type="button"
            onClick={handleCancel}
            className="btn btn-ghost"
            style={{ padding: '10px 14px' }}
          >
            Cancel
          </button>
        </div>
      </div>
    )
  }

  return (
    <div
      ref={setRef}
      className="panel"
      style={{
        padding: '12px 14px',
        borderColor: highlight ? 'var(--orange)' : undefined,
        boxShadow: highlight ? '0 0 14px var(--orange-glow)' : undefined,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            className="serif"
            style={{
              fontSize: 17,
              color: 'var(--cream)',
              lineHeight: 1.15,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {title}
          </div>
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: 'rgba(244,229,200,0.55)',
              marginTop: 2,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {artist}
          </div>
        </div>
        <VuMeter value={votes} max={maxVotes} showNumeric />
        <button
          type="button"
          onClick={() => { setEditTitle(title); setEditArtist(artist); setEditing(true) }}
          className="btn btn-ghost"
          style={{ padding: '6px 8px', fontSize: 10, flexShrink: 0 }}
          aria-label={`Edit ${title}`}
          title="Edit"
        >
          <Icon name="edit" size={14} />
        </button>
        <button
          type="button"
          onClick={onDelete}
          className="btn btn-ghost"
          style={{ padding: '6px 8px', fontSize: 10, flexShrink: 0 }}
          aria-label={`Remove ${title}`}
          title="Remove"
        >
          <Icon name="trash" size={14} />
        </button>
      </div>
    </div>
  )
}

function EmptySetlist() {
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
        Empty stage
      </div>
      <p style={{ marginTop: 6, fontSize: 13, fontWeight: 600 }}>
        Add a song above to get the setlist rolling.
      </p>
    </div>
  )
}

function SkeletonRows() {
  return (
    <>
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          className="panel"
          style={{ padding: 14, opacity: 0.5 }}
        >
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
