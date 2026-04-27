import { useState, type FormEvent } from 'react'
import Icon from './Icon'

interface AddSongFormProps {
  onAdd: (title: string, artist: string) => Promise<void>
}

export default function AddSongForm({ onAdd }: AddSongFormProps) {
  const [title, setTitle] = useState('')
  const [artist, setArtist] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!title.trim() || !artist.trim()) return
    setBusy(true)
    setError('')
    try {
      await onAdd(title.trim(), artist.trim())
      setTitle('')
      setArtist('')
    } catch (err) {
      const e = err as { code?: string; message?: string }
      if (e.code === 'duplicate') {
        setError('That song is already on the setlist.')
      } else {
        setError(e.message || 'Could not add the song.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form
      onSubmit={onSubmit}
      className="panel"
      style={{
        padding: 16,
        display: 'grid',
        gridTemplateColumns: '1fr',
        gap: 10,
      }}
    >
      <div className="label-cap">Add to setlist</div>
      <input
        className="input"
        placeholder="Song title"
        value={title}
        onChange={(e) => {
          setTitle(e.target.value)
          if (error) setError('')
        }}
        maxLength={200}
      />
      <input
        className="input"
        placeholder="Artist"
        value={artist}
        onChange={(e) => {
          setArtist(e.target.value)
          if (error) setError('')
        }}
        maxLength={200}
      />
      {error && (
        <div
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--orange-bright)',
            padding: '4px 0',
          }}
        >
          {error}
        </div>
      )}
      <button
        type="submit"
        className="btn btn-primary"
        disabled={busy || !title.trim() || !artist.trim()}
        style={{ justifyContent: 'center', padding: '12px 16px' }}
      >
        <Icon name="plus" size={14} /> {busy ? 'Adding…' : 'Add song'}
      </button>
    </form>
  )
}
