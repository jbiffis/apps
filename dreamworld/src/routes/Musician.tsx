import { useMemo, useState } from 'react'
import AddSongForm from '../components/AddSongForm'
import AppHeader from '../components/AppHeader'
import Icon from '../components/Icon'
import PasswordGate from '../components/PasswordGate'
import QrModal from '../components/QrModal'
import VuMeter from '../components/VuMeter'
import { useSongs } from '../hooks/useSongs'
import { addSong, deleteSong, resetVotes, updateSong } from '../lib/api'
import { clearAdminPassword, getAdminPassword } from '../lib/auth'

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
        'Reset all votes to zero? Use this between sets. Songs stay on the list.',
      )
    )
      return
    setResetting(true)
    setResetMsg('')
    try {
      const count = await resetVotes()
      setResetMsg(`Cleared votes on ${count} ${count === 1 ? 'song' : 'songs'}.`)
      if (songs) patch(songs.map((s) => ({ ...s, votes: 0, voted: false })))
      reload()
      setTimeout(() => setResetMsg(''), 3500)
    } catch {
      setResetMsg('Could not reset votes.')
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
          <Stat label="Votes" value={totalVotes} accent />
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
            <Icon name="reset" size={16} /> {resetting ? 'Resetting…' : 'Reset votes'}
          </button>
        </div>
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
            <span className="label-cap">Setlist · sorted by votes</span>
            {error && (
              <span
                style={{ fontSize: 11, color: 'var(--orange-bright)', fontWeight: 700 }}
              >
                {error}
              </span>
            )}
          </div>

          {songs === undefined && <SkeletonRows />}

          {songs && songs.length === 0 && <EmptySetlist />}

          {songs &&
            songs.map((s) => (
              <SongRow
                key={s.id}
                id={s.id}
                title={s.title}
                artist={s.artist}
                votes={s.votes}
                maxVotes={maxVotes || 1}
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
  onDelete,
  onEdit,
}: {
  id: string
  title: string
  artist: string
  votes: number
  maxVotes: number
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
    <div className="panel" style={{ padding: '12px 14px' }}>
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
