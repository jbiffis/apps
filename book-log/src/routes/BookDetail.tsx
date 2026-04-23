import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import BookCover from '../components/BookCover'
import Icon from '../components/Icon'
import StarRating from '../components/StarRating'
import { useBook, useBooks } from '../hooks/useBooks'
import { bookColor } from '../lib/colors'
import { deleteBook, updateBook } from '../lib/db'
import type { Book, ReadingStatus } from '../lib/types'

const EMOJI_RATINGS: { emoji: string; label: string }[] = [
  { emoji: '🤯', label: 'Mind blown' },
  { emoji: '😍', label: 'Loved it' },
  { emoji: '😄', label: 'Good one' },
  { emoji: '😐', label: 'Meh' },
  { emoji: '😴', label: 'Sleepy' },
]

export default function BookDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const book = useBook(id)
  const allBooks = useBooks() ?? []

  const [notes, setNotes] = useState('')
  const [notesDirty, setNotesDirty] = useState(false)
  const [pageInput, setPageInput] = useState('')
  const [showPageEditor, setShowPageEditor] = useState(false)
  const [emoji, setEmoji] = useState<string | undefined>()
  const [celebrate, setCelebrate] = useState(false)

  useEffect(() => {
    if (book) {
      if (!notesDirty) setNotes(book.notes ?? '')
      setEmoji(pickEmoji(book))
    }
  }, [book, notesDirty])

  if (book === undefined) {
    return (
      <div
        className="paper-bg"
        style={{ minHeight: '100%', padding: '40px 16px', color: 'var(--ink-mute)' }}
      >
        Loading…
      </div>
    )
  }

  if (book === null) {
    return (
      <div className="paper-bg" style={{ minHeight: '100%', padding: '40px 16px' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 48 }}>🤷</div>
          <p className="serif" style={{ fontSize: 22, marginTop: 8 }}>
            We couldn’t find that book.
          </p>
          <Link to="/shelves" className="btn btn-primary" style={{ marginTop: 12 }}>
            ← Back to shelves
          </Link>
        </div>
      </div>
    )
  }

  const color = book.coverUrl ? bookColor(book.id) : bookColor(book.id)
  const isReading = book.status === 'reading'
  const isFinished = book.status === 'read'
  const isTbr = book.status === 'want-to-read'
  const pages = book.pageCount ?? 0
  const progress = book.progress ?? 0
  const pct = pages > 0 ? Math.round((progress / pages) * 100) : 0

  async function setStatus(status: ReadingStatus) {
    if (!book) return
    const changes: Partial<Book> = { status }
    if (status === 'read') {
      if (!book.dateFinished) changes.dateFinished = Date.now()
      if (pages > 0) changes.progress = pages
      setCelebrate(true)
      setTimeout(() => setCelebrate(false), 1800)
    }
    if (status === 'reading') {
      if (!book.progress) changes.progress = 0
    }
    await updateBook(book.id, changes)
  }

  async function setRating(r: Book['rating']) {
    if (!book) return
    await updateBook(book.id, { rating: r })
  }

  async function saveNotes() {
    if (!book) return
    await updateBook(book.id, { notes: notes.trim() || undefined })
    setNotesDirty(false)
  }

  async function savePage() {
    if (!book) return
    const n = Math.max(0, Math.min(pages || 99999, Number(pageInput) || 0))
    const changes: Partial<Book> = { progress: n }
    if (pages > 0 && n >= pages) {
      changes.status = 'read'
      if (!book.dateFinished) changes.dateFinished = Date.now()
    }
    await updateBook(book.id, changes)
    setShowPageEditor(false)
    setPageInput('')
  }

  async function onDelete() {
    if (!book) return
    if (!confirm(`Remove "${book.title}" from your shelves?`)) return
    await deleteBook(book.id)
    navigate('/shelves')
  }

  const others = allBooks.filter((b) => b.id !== book.id).slice(0, 3)

  return (
    <div className="paper-bg" style={{ minHeight: '100%', paddingBottom: 120 }}>
      {celebrate && <Confetti />}

      {/* Colored header */}
      <div
        style={{
          background: color,
          padding: '16px 16px 70px',
          position: 'relative',
          borderBottom: '2px solid var(--line)',
        }}
      >
        <button
          className="btn"
          onClick={() => navigate(-1)}
          style={{ background: 'var(--paper)' }}
        >
          <Icon name="arrow-left" size={14} /> Back
        </button>
      </div>

      <div style={{ padding: '0 16px', marginTop: -60 }}>
        {/* Cover + title block */}
        <div style={{ textAlign: 'center', marginBottom: 20 }}>
          <div
            style={{
              display: 'inline-block',
              position: 'relative',
              transform: 'rotate(-3deg)',
            }}
          >
            <BookCover book={book} size="lg" />
            <div className="tape tape-pink" style={{ top: -12, left: '30%' }} />
          </div>
          <div style={{ marginTop: 16 }}>
            <div
              style={{
                display: 'flex',
                gap: 6,
                marginBottom: 8,
                flexWrap: 'wrap',
                justifyContent: 'center',
              }}
            >
              {book.tags.slice(0, 2).map((t) => (
                <div key={t} className="chip chip-active">
                  {t}
                </div>
              ))}
              {book.pageCount && (
                <div className="chip">
                  <Icon name="book-open" size={12} /> {book.pageCount} pages
                </div>
              )}
            </div>
            <h1 className="serif" style={{ fontSize: 26, lineHeight: 1.05 }}>
              {book.title}
            </h1>
            <div
              style={{
                fontSize: 14,
                color: 'var(--ink-soft)',
                fontWeight: 700,
                marginTop: 4,
              }}
            >
              by {book.authors.join(', ') || 'unknown'}
            </div>
          </div>
        </div>

        {/* Primary actions */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: isFinished ? '1fr' : '1fr 1fr',
            gap: 8,
            marginBottom: 20,
          }}
        >
          {isTbr && (
            <button
              className="btn btn-primary"
              onClick={() => setStatus('reading')}
              style={{ justifyContent: 'center', gridColumn: '1 / -1' }}
            >
              <Icon name="book-open" size={16} stroke="#fff" /> Start reading
            </button>
          )}
          {isReading && (
            <>
              <button
                className="btn btn-accent-3"
                onClick={() => setShowPageEditor((v) => !v)}
                style={{ justifyContent: 'center' }}
              >
                <Icon name="edit" size={14} stroke="#fff" /> Update page
              </button>
              <button
                className="btn btn-primary"
                onClick={() => setStatus('read')}
                style={{ justifyContent: 'center' }}
              >
                <Icon name="check" size={16} stroke="#fff" /> I finished it!
              </button>
            </>
          )}
          {isFinished && (
            <button
              className="btn btn-accent-2"
              onClick={() => setStatus('reading')}
              style={{ justifyContent: 'center' }}
            >
              <Icon name="book-open" size={14} /> Read it again
            </button>
          )}
        </div>

        {/* Progress card */}
        {isReading && (
          <div className="card" style={{ padding: 18, marginBottom: 16 }}>
            <h2 className="serif" style={{ fontSize: 18, marginBottom: 10 }}>
              How far are you?
            </h2>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginBottom: 10 }}>
              <div className="serif" style={{ fontSize: 34, fontWeight: 800, lineHeight: 1 }}>
                {progress}
              </div>
              <div style={{ color: 'var(--ink-mute)', fontWeight: 700, fontSize: 13 }}>
                of {pages || '???'} pages
              </div>
            </div>
            <div className="progress" style={{ height: 14 }}>
              <div
                className="progress-fill"
                style={{ width: `${pct}%` }}
              />
            </div>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                marginTop: 6,
                fontSize: 11,
                fontWeight: 800,
                color: 'var(--ink-soft)',
              }}
            >
              <span>{pct}% done</span>
              {pages > 0 && <span>{Math.max(0, pages - progress)} pages to go</span>}
            </div>

            {showPageEditor && (
              <div
                style={{
                  marginTop: 14,
                  padding: 12,
                  background: 'var(--bg-2)',
                  border: '2px dashed var(--ink-mute)',
                  borderRadius: 12,
                }}
              >
                <div
                  style={{
                    fontSize: 11,
                    fontWeight: 800,
                    textTransform: 'uppercase',
                    letterSpacing: '0.08em',
                    color: 'var(--ink-soft)',
                    marginBottom: 8,
                  }}
                >
                  What page are you on?
                </div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <input
                    type="number"
                    inputMode="numeric"
                    value={pageInput || progress}
                    onChange={(e) => setPageInput(e.target.value)}
                    className="input"
                    style={{ flex: 1, textAlign: 'center', fontSize: 20, fontFamily: 'Fraunces, serif', fontWeight: 800 }}
                    min={0}
                    max={pages || undefined}
                  />
                  <button className="btn btn-primary" onClick={savePage}>
                    Save
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Rating + emoji reaction for finished */}
        {isFinished && (
          <div
            className="card"
            style={{ padding: 18, marginBottom: 16, background: 'var(--accent-2)' }}
          >
            <h2 className="serif" style={{ fontSize: 18, marginBottom: 10 }}>
              Your rating
            </h2>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 14 }}>
              <StarRating rating={book.rating} size={32} interactive onRate={setRating} />
            </div>
            <div
              style={{
                display: 'flex',
                justifyContent: 'center',
                gap: 8,
                flexWrap: 'wrap',
              }}
            >
              {EMOJI_RATINGS.map((r) => {
                const active = r.emoji === emoji
                return (
                  <button
                    key={r.emoji}
                    onClick={() => {
                      setEmoji(r.emoji)
                      const tags = book.tags.filter(
                        (t) => !EMOJI_RATINGS.some((x) => x.emoji === t),
                      )
                      updateBook(book.id, { tags: [...tags, r.emoji] })
                    }}
                    style={{
                      width: 52,
                      height: 52,
                      borderRadius: '50%',
                      border: '2px solid var(--line)',
                      background: active ? 'var(--paper)' : 'rgba(255,255,255,0.45)',
                      fontSize: 24,
                      cursor: 'pointer',
                      padding: 0,
                      boxShadow: active ? 'var(--shadow-sm)' : 'none',
                    }}
                    aria-label={r.label}
                    title={r.label}
                  >
                    {r.emoji}
                  </button>
                )
              })}
            </div>
            {emoji && (
              <div
                style={{
                  textAlign: 'center',
                  marginTop: 8,
                  fontSize: 13,
                  fontWeight: 800,
                }}
              >
                {EMOJI_RATINGS.find((r) => r.emoji === emoji)?.label}
              </div>
            )}
          </div>
        )}

        {/* Notes / review */}
        <div
          className="card"
          style={{
            padding: 18,
            marginBottom: 16,
            background: 'var(--sticker-pink)',
            position: 'relative',
          }}
        >
          <div className="tape tape-green right" />
          <h2 className="serif" style={{ fontSize: 18, marginBottom: 10 }}>
            {isFinished ? 'Your review' : 'Your reading notes'}
          </h2>
          <textarea
            value={notes}
            onChange={(e) => {
              setNotes(e.target.value)
              setNotesDirty(true)
            }}
            onBlur={() => notesDirty && saveNotes()}
            rows={4}
            placeholder={
              isFinished
                ? 'What did you think? Favorite moment? Write it down!'
                : 'Thoughts so far? Jot them down here.'
            }
            style={{
              width: '100%',
              border: '2px dashed var(--ink-soft)',
              borderRadius: 12,
              padding: 10,
              background: 'var(--paper)',
              fontFamily: 'Fraunces, Georgia, serif',
              fontSize: 15,
              lineHeight: 1.45,
              color: 'var(--ink)',
              fontStyle: notes ? 'italic' : 'normal',
              outline: 'none',
              resize: 'vertical',
              fontWeight: 600,
            }}
          />
        </div>

        {/* Tags */}
        {(book.tags.length > 0 || book.publisher) && (
          <div className="card" style={{ padding: 16, marginBottom: 16 }}>
            {book.tags.length > 0 && (
              <div>
                <div
                  style={{
                    fontSize: 11,
                    fontWeight: 800,
                    textTransform: 'uppercase',
                    letterSpacing: '0.08em',
                    color: 'var(--ink-mute)',
                    marginBottom: 6,
                  }}
                >
                  Tags
                </div>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  {book.tags
                    .filter((t) => !EMOJI_RATINGS.some((r) => r.emoji === t))
                    .map((t) => (
                      <div key={t} className="chip">
                        {t}
                      </div>
                    ))}
                </div>
              </div>
            )}
            {book.publisher && (
              <div
                style={{
                  marginTop: 10,
                  fontSize: 12,
                  color: 'var(--ink-mute)',
                  fontWeight: 700,
                }}
              >
                {book.publisher}
                {book.publishDate ? ` · ${book.publishDate}` : ''}
              </div>
            )}
          </div>
        )}

        {/* Kids also liked */}
        {others.length > 0 && (
          <div className="card" style={{ padding: 16, marginBottom: 16, background: 'var(--bg-2)' }}>
            <h3 className="serif" style={{ fontSize: 16, marginBottom: 10 }}>
              Also on your shelf
            </h3>
            <div style={{ display: 'flex', gap: 10, overflowX: 'auto' }} className="no-scrollbar">
              {others.map((b) => (
                <Link
                  key={b.id}
                  to={`/book/${b.id}`}
                  style={{ flexShrink: 0, textDecoration: 'none' }}
                >
                  <BookCover book={b} size="xs" />
                </Link>
              ))}
            </div>
          </div>
        )}

        {/* Danger zone */}
        <div style={{ marginTop: 24 }}>
          <button
            onClick={onDelete}
            className="btn"
            style={{
              background: 'var(--paper)',
              color: 'var(--accent-4)',
              borderColor: 'var(--accent-4)',
            }}
          >
            <Icon name="trash" size={14} /> Remove from my shelves
          </button>
        </div>
      </div>
    </div>
  )
}

function pickEmoji(book: Book): string | undefined {
  const emojis = ['🤯', '😍', '😄', '😐', '😴']
  return book.tags.find((t) => emojis.includes(t))
}

function Confetti() {
  const pieces = Array.from({ length: 24 })
  const colors = [
    'var(--accent-1)',
    'var(--accent-2)',
    'var(--accent-3)',
    'var(--sticker-pink)',
    'var(--sticker-green)',
    'var(--sticker-blue)',
  ]
  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        pointerEvents: 'none',
        overflow: 'hidden',
        zIndex: 100,
      }}
    >
      {pieces.map((_, i) => {
        const left = Math.random() * 100
        const delay = Math.random() * 0.4
        const duration = 1.2 + Math.random() * 1.2
        const size = 8 + Math.random() * 8
        const color = colors[i % colors.length]
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: `${left}%`,
              top: 0,
              width: size,
              height: size,
              background: color,
              border: '1.5px solid var(--line)',
              borderRadius: i % 3 === 0 ? '50%' : '2px',
              animation: `confetti-fall ${duration}s ${delay}s ease-in forwards`,
            }}
          />
        )
      })}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <div
          className="pop-in card"
          style={{
            padding: '18px 24px',
            background: 'var(--paper)',
            textAlign: 'center',
          }}
        >
          <div style={{ fontSize: 44 }}>🎉</div>
          <div className="serif" style={{ fontSize: 20, marginTop: 4 }}>
            Book conquered!
          </div>
        </div>
      </div>
    </div>
  )
}
