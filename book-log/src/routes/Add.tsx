import { lazy, Suspense, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import BookCover from '../components/BookCover'
import Icon from '../components/Icon'
import OwlMascot from '../components/OwlMascot'
import { addBook, getBook } from '../lib/db'
import { isValidIsbn, normalizeIsbn, toIsbn13 } from '../lib/isbn'
import { lookupBook, type BookLookup } from '../lib/openlibrary'
import { statusFromShelf } from '../lib/status'
import type { Book } from '../lib/types'

const BarcodeScanner = lazy(() => import('../components/BarcodeScanner'))

type Mode = 'choose' | 'scan' | 'type'

type State =
  | { kind: 'idle' }
  | { kind: 'looking-up'; isbn13: string }
  | { kind: 'error'; message: string }
  | { kind: 'found'; lookup: BookLookup }
  | { kind: 'duplicate'; book: Book }
  | { kind: 'saving' }
  | { kind: 'done'; savedBook: Book }

export default function Add() {
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>('choose')
  const [isbn, setIsbn] = useState('')
  const [state, setState] = useState<State>({ kind: 'idle' })
  const [shelf, setShelf] = useState<'reading' | 'tbr' | 'finished'>('reading')
  const [page, setPage] = useState(0)

  async function handleIsbn(raw: string) {
    const normalized = normalizeIsbn(raw)
    if (!isValidIsbn(normalized)) {
      setState({
        kind: 'error',
        message: 'That number doesn’t look quite right — try again?',
      })
      return
    }
    const isbn13 = toIsbn13(normalized)
    if (!isbn13) {
      setState({ kind: 'error', message: 'Could not read that number.' })
      return
    }
    const existing = await getBook(isbn13)
    if (existing) {
      setState({ kind: 'duplicate', book: existing })
      return
    }
    setState({ kind: 'looking-up', isbn13 })
    const lookup = await lookupBook(isbn13)
    if (!lookup) {
      setState({
        kind: 'error',
        message: 'We couldn’t find that book. Try typing the number again, or double-check the barcode.',
      })
      return
    }
    setState({ kind: 'found', lookup })
  }

  async function onTypeSubmit(e: FormEvent) {
    e.preventDefault()
    await handleIsbn(isbn)
  }

  async function save() {
    if (state.kind !== 'found') return
    setState({ kind: 'saving' })
    const status = statusFromShelf(shelf)
    const book: Book = {
      ...state.lookup,
      status,
      progress:
        shelf === 'reading'
          ? page
          : shelf === 'finished'
            ? state.lookup.pageCount ?? 0
            : 0,
      dateAdded: Date.now(),
      dateFinished: shelf === 'finished' ? Date.now() : undefined,
      tags: [],
    }
    await addBook(book)
    setState({ kind: 'done', savedBook: book })
  }

  const step = (() => {
    if (state.kind === 'done') return 3
    if (state.kind === 'found') return 2
    return 1
  })()

  return (
    <div className="paper-bg" style={{ minHeight: '100%', padding: '20px 16px 120px' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          gap: 8,
        }}
      >
        <button
          className="btn"
          onClick={() => {
            if (state.kind === 'found') {
              setState({ kind: 'idle' })
              return
            }
            if (mode !== 'choose') {
              setMode('choose')
              setState({ kind: 'idle' })
              return
            }
            navigate(-1)
          }}
        >
          <Icon name="arrow-left" size={14} /> {mode === 'choose' ? 'Nevermind' : 'Back'}
        </button>
        <div style={{ display: 'flex', gap: 6 }}>
          {[1, 2, 3].map((n) => (
            <div
              key={n}
              style={{
                width: 26,
                height: 26,
                borderRadius: '50%',
                border: '2px solid var(--line)',
                background: step >= n ? 'var(--accent-1)' : 'var(--paper)',
                color: step >= n ? '#fff' : 'var(--ink-mute)',
                fontFamily: 'Fraunces, serif',
                fontWeight: 800,
                fontSize: 13,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {n}
            </div>
          ))}
        </div>
      </div>

      <div style={{ textAlign: 'center', marginBottom: 18 }}>
        <div
          style={{
            fontSize: 11,
            fontWeight: 800,
            letterSpacing: '0.1em',
            textTransform: 'uppercase',
            color: 'var(--ink-mute)',
          }}
        >
          Step {step} of 3
        </div>
        <h1 className="serif" style={{ fontSize: 26, marginTop: 4, lineHeight: 1.1 }}>
          {step === 1 && 'What book are we adding?'}
          {step === 2 && 'Which shelf does it go on?'}
          {step === 3 && 'All set! 🎉'}
        </h1>
      </div>

      {step === 1 && state.kind !== 'found' && (
        <>
          {mode === 'choose' && (
            <ChooseMode onPick={(m) => setMode(m)} />
          )}
          {mode === 'scan' && (
            <ScanPanel
              onDetected={(code) => {
                setMode('choose')
                handleIsbn(code)
              }}
              onCancel={() => setMode('choose')}
            />
          )}
          {mode === 'type' && (
            <TypePanel
              isbn={isbn}
              setIsbn={setIsbn}
              onSubmit={onTypeSubmit}
              busy={state.kind === 'looking-up'}
            />
          )}

          {state.kind === 'looking-up' && (
            <div
              className="card"
              style={{ padding: 16, marginTop: 14, textAlign: 'center' }}
            >
              <div className="float">
                <OwlMascot size={64} mood="reading" />
              </div>
              <div className="serif" style={{ fontSize: 17, marginTop: 4 }}>
                Hootie is looking it up…
              </div>
              <div style={{ fontSize: 12, color: 'var(--ink-mute)', fontWeight: 700 }}>
                ISBN {state.isbn13}
              </div>
            </div>
          )}

          {state.kind === 'error' && (
            <div
              className="card"
              style={{
                padding: 14,
                marginTop: 14,
                background: 'var(--sticker-pink)',
              }}
            >
              <div className="serif" style={{ fontSize: 16 }}>
                Hmm. 🤔
              </div>
              <p style={{ margin: '4px 0 0', fontSize: 13, fontWeight: 700 }}>
                {state.message}
              </p>
            </div>
          )}

          {state.kind === 'duplicate' && (
            <div
              className="card"
              style={{
                padding: 14,
                marginTop: 14,
                background: 'var(--sticker-yellow)',
              }}
            >
              <div className="serif" style={{ fontSize: 16 }}>
                You already have that one! 📚
              </div>
              <div style={{ fontSize: 13, fontWeight: 700, marginTop: 2 }}>
                <em>{state.book.title}</em> is already on your shelf.
              </div>
              <div style={{ display: 'flex', gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
                <button
                  className="btn"
                  onClick={() => setState({ kind: 'idle' })}
                >
                  Add a different one
                </button>
                <Link
                  to={`/book/${state.book.id}`}
                  className="btn btn-primary"
                >
                  Go see it <Icon name="arrow-right" size={14} stroke="#fff" />
                </Link>
              </div>
            </div>
          )}
        </>
      )}

      {step === 2 && state.kind === 'found' && (
        <ShelfPicker
          lookup={state.lookup}
          shelf={shelf}
          setShelf={setShelf}
          page={page}
          setPage={setPage}
          onConfirm={save}
        />
      )}

      {step === 3 && state.kind === 'done' && (
        <DoneScreen book={state.savedBook} onHome={() => navigate('/')} onShelf={() => navigate('/shelves')} />
      )}

      {state.kind === 'saving' && (
        <div className="card" style={{ padding: 14, marginTop: 14, textAlign: 'center' }}>
          <div className="serif" style={{ fontSize: 16 }}>
            Putting it on your shelf…
          </div>
        </div>
      )}
    </div>
  )
}

function ChooseMode({ onPick }: { onPick: (m: Mode) => void }) {
  return (
    <div style={{ display: 'grid', gap: 12 }}>
      <button
        onClick={() => onPick('scan')}
        className="card"
        style={{
          padding: 18,
          cursor: 'pointer',
          background: 'var(--accent-3)',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          textAlign: 'left',
          border: '2px solid var(--line)',
        }}
      >
        <div
          style={{
            width: 52,
            height: 52,
            borderRadius: '50%',
            background: 'rgba(255,255,255,0.25)',
            border: '2px solid var(--line)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Icon name="camera" size={24} stroke="#fff" />
        </div>
        <div style={{ flex: 1 }}>
          <div className="serif" style={{ fontSize: 20, fontWeight: 700 }}>
            Scan the barcode
          </div>
          <div style={{ fontSize: 13, fontWeight: 700, opacity: 0.9 }}>
            Fastest way — point your camera at the book
          </div>
        </div>
        <Icon name="arrow-right" size={20} stroke="#fff" />
      </button>

      <button
        onClick={() => onPick('type')}
        className="card"
        style={{
          padding: 18,
          cursor: 'pointer',
          background: 'var(--accent-2)',
          color: 'var(--ink)',
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          textAlign: 'left',
          border: '2px solid var(--line)',
        }}
      >
        <div
          style={{
            width: 52,
            height: 52,
            borderRadius: '50%',
            background: 'var(--paper)',
            border: '2px solid var(--line)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Icon name="edit" size={22} />
        </div>
        <div style={{ flex: 1 }}>
          <div className="serif" style={{ fontSize: 20, fontWeight: 700 }}>
            Type it out myself
          </div>
          <div style={{ fontSize: 13, fontWeight: 700 }}>
            Enter the 13-digit ISBN from the back
          </div>
        </div>
        <Icon name="arrow-right" size={20} />
      </button>
    </div>
  )
}

function ScanPanel({
  onDetected,
  onCancel,
}: {
  onDetected: (code: string) => void
  onCancel: () => void
}) {
  return (
    <div>
      <div
        style={{
          overflow: 'hidden',
          borderRadius: 18,
          border: '2px solid var(--line)',
          boxShadow: 'var(--shadow)',
          marginBottom: 12,
        }}
      >
        <Suspense
          fallback={
            <div
              style={{
                aspectRatio: '3/4',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: '#000',
                color: '#fff',
              }}
            >
              Loading scanner…
            </div>
          }
        >
          <BarcodeScanner onDetected={onDetected} />
        </Suspense>
      </div>
      <p
        style={{
          fontSize: 12,
          fontWeight: 700,
          color: 'var(--ink-soft)',
          textAlign: 'center',
          margin: '0 0 10px',
        }}
      >
        Line up the barcode in the box — we’ll grab it automatically.
      </p>
      <button
        className="btn"
        onClick={onCancel}
        style={{ width: '100%', justifyContent: 'center' }}
      >
        Cancel
      </button>
    </div>
  )
}

function TypePanel({
  isbn,
  setIsbn,
  onSubmit,
  busy,
}: {
  isbn: string
  setIsbn: (v: string) => void
  onSubmit: (e: FormEvent) => void
  busy: boolean
}) {
  return (
    <form onSubmit={onSubmit}>
      <div style={{ marginBottom: 10 }}>
        <div
          style={{
            fontSize: 11,
            fontWeight: 800,
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            color: 'var(--ink-mute)',
            marginBottom: 6,
          }}
        >
          Book number (ISBN)
        </div>
        <input
          className="input"
          type="text"
          inputMode="numeric"
          autoComplete="off"
          autoFocus
          value={isbn}
          onChange={(e) => setIsbn(e.target.value)}
          placeholder="9780441172719"
          style={{ fontSize: 18, textAlign: 'center', fontFamily: 'Fraunces, serif' }}
        />
        <div
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--ink-mute)',
            marginTop: 6,
          }}
        >
          On the back cover, near the barcode. It starts with <strong>978</strong>.
        </div>
      </div>
      <button
        type="submit"
        className="btn btn-primary"
        disabled={busy || isbn.trim().length === 0}
        style={{ width: '100%', justifyContent: 'center' }}
      >
        {busy ? 'Looking up…' : 'Find my book'}
        <Icon name="arrow-right" size={14} stroke="#fff" />
      </button>
    </form>
  )
}

function ShelfPicker({
  lookup,
  shelf,
  setShelf,
  page,
  setPage,
  onConfirm,
}: {
  lookup: BookLookup
  shelf: 'reading' | 'tbr' | 'finished'
  setShelf: (s: 'reading' | 'tbr' | 'finished') => void
  page: number
  setPage: (p: number) => void
  onConfirm: () => void
}) {
  const options = [
    {
      id: 'reading' as const,
      label: "I'm reading it right now",
      desc: 'Onto the Currently Reading shelf',
      icon: 'book-open' as const,
      color: 'var(--accent-1)',
    },
    {
      id: 'tbr' as const,
      label: 'I wanna read it later',
      desc: 'Save it for future you',
      icon: 'bookmark' as const,
      color: 'var(--accent-3)',
    },
    {
      id: 'finished' as const,
      label: 'I already finished it',
      desc: 'Add to your done-and-dusted pile',
      icon: 'check' as const,
      color: 'var(--accent-2)',
    },
  ]

  return (
    <div>
      <div
        className="card"
        style={{
          padding: 14,
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          marginBottom: 18,
        }}
      >
        <BookCover book={{ ...lookup, id: lookup.id }} size="sm" />
        <div style={{ flex: 1, minWidth: 0 }}>
          <h2
            className="serif"
            style={{
              fontSize: 17,
              lineHeight: 1.1,
              overflow: 'hidden',
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
            }}
          >
            {lookup.title}
          </h2>
          <div
            style={{
              fontSize: 12,
              fontWeight: 700,
              color: 'var(--ink-mute)',
              marginTop: 2,
            }}
          >
            {lookup.authors.join(', ') || 'unknown'}
          </div>
          {lookup.pageCount && (
            <div className="chip" style={{ marginTop: 6, fontSize: 10, padding: '2px 8px' }}>
              {lookup.pageCount} pages
            </div>
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gap: 10 }}>
        {options.map((opt) => {
          const active = shelf === opt.id
          return (
            <button
              key={opt.id}
              onClick={() => setShelf(opt.id)}
              className="card"
              style={{
                padding: 14,
                cursor: 'pointer',
                background: active ? opt.color : 'var(--paper)',
                color: active ? '#fff' : 'var(--ink)',
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                boxShadow: active ? 'var(--shadow-lg)' : 'var(--shadow-sm)',
                textAlign: 'left',
                width: '100%',
              }}
            >
              <div
                style={{
                  width: 42,
                  height: 42,
                  borderRadius: '50%',
                  background: active ? 'rgba(255,255,255,0.25)' : opt.color,
                  border: '2px solid var(--line)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <Icon name={opt.icon} size={18} stroke="#fff" />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="serif" style={{ fontSize: 16, fontWeight: 700 }}>
                  {opt.label}
                </div>
                <div style={{ fontSize: 12, fontWeight: 700, opacity: 0.85 }}>
                  {opt.desc}
                </div>
              </div>
              {active && (
                <div
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    background: 'var(--paper)',
                    border: '2px solid var(--line)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  <Icon name="check" size={12} stroke="var(--ink)" />
                </div>
              )}
            </button>
          )
        })}
      </div>

      {shelf === 'reading' && lookup.pageCount && (
        <div className="card" style={{ padding: 14, marginTop: 14 }}>
          <div
            style={{
              fontSize: 11,
              fontWeight: 800,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              color: 'var(--ink-mute)',
              marginBottom: 8,
            }}
          >
            What page are you on?
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <button
              type="button"
              className="btn"
              onClick={() => setPage(Math.max(0, page - 10))}
            >
              −10
            </button>
            <div
              className="serif"
              style={{
                fontSize: 28,
                fontWeight: 800,
                flex: 1,
                textAlign: 'center',
              }}
            >
              {page}
            </div>
            <button
              type="button"
              className="btn"
              onClick={() => setPage(Math.min((lookup.pageCount ?? 9999), page + 10))}
            >
              +10
            </button>
          </div>
          <div
            style={{
              fontSize: 12,
              fontWeight: 700,
              color: 'var(--ink-mute)',
              textAlign: 'center',
              marginTop: 4,
            }}
          >
            of {lookup.pageCount}
          </div>
        </div>
      )}

      <button
        onClick={onConfirm}
        className="btn btn-primary"
        style={{
          width: '100%',
          justifyContent: 'center',
          marginTop: 18,
          padding: '14px 18px',
          fontSize: 15,
        }}
      >
        Add to my {shelf === 'tbr' ? 'wishlist' : 'shelves'}
        <Icon name="arrow-right" size={16} stroke="#fff" />
      </button>
    </div>
  )
}

function DoneScreen({
  book,
  onHome,
  onShelf,
}: {
  book: Book
  onHome: () => void
  onShelf: () => void
}) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div
        className="pop-in"
        style={{ position: 'relative', display: 'inline-block', marginBottom: 20 }}
      >
        <BookCover book={book} size="xl" tilt={-4} />
        <div
          style={{ position: 'absolute', top: -16, right: -20, fontSize: 40 }}
          className="wobble"
        >
          ✨
        </div>
        <div
          style={{ position: 'absolute', bottom: 6, left: -24, fontSize: 34 }}
          className="wobble"
        >
          🎉
        </div>
      </div>
      <h2 className="serif" style={{ fontSize: 24, marginBottom: 8 }}>
        Booyah! It’s on your shelf.
      </h2>
      <p
        style={{
          fontSize: 14,
          color: 'var(--ink-soft)',
          fontWeight: 700,
          marginBottom: 20,
          padding: '0 16px',
        }}
      >
        Hootie put <em>{book.title}</em> right where it belongs. Happy reading, bookworm! 🐛
      </p>
      <div
        style={{
          display: 'flex',
          gap: 10,
          justifyContent: 'center',
          flexWrap: 'wrap',
        }}
      >
        <button className="btn" onClick={onHome}>
          Back to home
        </button>
        <button className="btn btn-primary" onClick={onShelf}>
          See my shelf <Icon name="arrow-right" size={14} stroke="#fff" />
        </button>
      </div>
    </div>
  )
}
