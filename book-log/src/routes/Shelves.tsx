import { useState } from 'react'
import { Link } from 'react-router-dom'
import BookCover from '../components/BookCover'
import Icon from '../components/Icon'
import StarRating from '../components/StarRating'
import { useBooks } from '../hooks/useBooks'
import type { Book, ReadingStatus } from '../lib/types'

type Shelf = 'reading' | 'tbr' | 'wishlist' | 'finished'

const TABS: {
  id: Shelf
  label: string
  icon: 'book-open' | 'bookmark' | 'heart' | 'check'
  color: string
  status: ReadingStatus
}[] = [
  {
    id: 'reading',
    label: 'Reading',
    icon: 'book-open',
    color: 'var(--accent-1)',
    status: 'reading',
  },
  {
    id: 'tbr',
    label: 'Up next',
    icon: 'bookmark',
    color: 'var(--accent-3)',
    status: 'want-to-read',
  },
  {
    id: 'wishlist',
    label: 'Wishlist',
    icon: 'heart',
    color: 'var(--accent-4)',
    status: 'wishlist',
  },
  {
    id: 'finished',
    label: 'Finished',
    icon: 'check',
    color: 'var(--accent-2)',
    status: 'read',
  },
]

export default function Shelves() {
  const books = useBooks()
  const [tab, setTab] = useState<Shelf>('reading')
  const [query, setQuery] = useState('')

  const loading = books === undefined
  const list = books ?? []
  const counts = {
    reading: list.filter((b) => b.status === 'reading').length,
    tbr: list.filter((b) => b.status === 'want-to-read').length,
    wishlist: list.filter((b) => b.status === 'wishlist').length,
    finished: list.filter((b) => b.status === 'read').length,
  }

  const activeStatus = TABS.find((t) => t.id === tab)!.status
  const q = query.trim().toLowerCase()
  const books_ = list.filter((b) => {
    if (b.status !== activeStatus) return false
    if (!q) return true
    return (
      b.title.toLowerCase().includes(q) ||
      b.authors.join(' ').toLowerCase().includes(q) ||
      b.tags.join(' ').toLowerCase().includes(q)
    )
  })

  return (
    <div className="paper-bg" style={{ minHeight: '100%', padding: '20px 16px 120px' }}>
      <div style={{ marginBottom: 16 }}>
        <div
          style={{
            fontSize: 11,
            fontWeight: 800,
            letterSpacing: '0.1em',
            textTransform: 'uppercase',
            color: 'var(--ink-mute)',
          }}
        >
          Your clubhouse shelves
        </div>
        <h1 className="serif" style={{ fontSize: 30, marginTop: 2 }}>
          My Shelves
        </h1>
      </div>

      {/* Search */}
      {list.length > 0 && (
        <div style={{ position: 'relative', marginBottom: 14 }}>
          <input
            className="input"
            placeholder="Find a book…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ paddingLeft: 40 }}
          />
          <div style={{ position: 'absolute', left: 14, top: 13, pointerEvents: 'none' }}>
            <Icon name="search" size={18} stroke="var(--ink-mute)" />
          </div>
        </div>
      )}

      {/* Shelf tabs — paper folder tabs */}
      <div
        className="no-scrollbar"
        style={{ display: 'flex', gap: 4, overflowX: 'auto', paddingBottom: 0 }}
      >
        {TABS.map((t) => {
          const active = tab === t.id
          return (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              style={{
                border: '2px solid var(--line)',
                borderBottom: active ? '2px solid var(--paper)' : '2px solid var(--line)',
                background: active ? 'var(--paper)' : 'var(--bg-2)',
                padding: '10px 14px',
                fontFamily: 'Nunito, sans-serif',
                fontWeight: 800,
                fontSize: 13,
                borderRadius: '12px 12px 0 0',
                cursor: 'pointer',
                color: active ? 'var(--ink)' : 'var(--ink-soft)',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                marginBottom: -2,
                position: 'relative',
                zIndex: active ? 2 : 1,
                flexShrink: 0,
              }}
            >
              <Icon name={t.icon} size={14} />
              {t.label}
              <span
                style={{
                  background: t.color,
                  color: '#fff',
                  borderRadius: 999,
                  padding: '1px 7px',
                  fontSize: 10,
                  border: '1.5px solid var(--line)',
                  fontWeight: 800,
                }}
              >
                {counts[t.id]}
              </span>
            </button>
          )
        })}
      </div>

      {/* Shelf body */}
      <div
        className="card"
        style={{
          padding: 18,
          minHeight: 280,
          borderRadius: '0 18px 18px 18px',
          marginTop: 0,
        }}
      >
        {loading ? (
          <p style={{ color: 'var(--ink-mute)', fontWeight: 700 }}>Loading…</p>
        ) : books_.length === 0 ? (
          <EmptyShelf tab={tab} />
        ) : tab === 'reading' ? (
          <ReadingList books={books_} />
        ) : tab === 'tbr' ? (
          <CoverGrid
            books={books_}
            heading="Future you says thanks"
            countLabel={`${books_.length} waiting`}
          />
        ) : tab === 'wishlist' ? (
          <CoverGrid
            books={books_}
            heading="Dreaming about these 💭"
            countLabel={`${books_.length} on the list`}
          />
        ) : (
          <FinishedList books={books_} />
        )}
      </div>
    </div>
  )
}

function EmptyShelf({ tab }: { tab: Shelf }) {
  const copy = {
    reading: {
      title: 'No books in progress',
      desc: 'Start a book and it’ll show up here.',
    },
    tbr: {
      title: 'Nothing up next yet',
      desc: 'Save books you can’t wait to crack open.',
    },
    wishlist: {
      title: 'Your wishlist is empty',
      desc: 'Add books you’d love to get your hands on someday.',
    },
    finished: {
      title: 'No conquered books yet',
      desc: 'Finish a book and it lands here. 🏆',
    },
  }[tab]
  return (
    <div style={{ textAlign: 'center', padding: '22px 0' }}>
      <div style={{ fontSize: 44 }}>📚</div>
      <div className="serif" style={{ fontSize: 20, marginTop: 6 }}>
        {copy.title}
      </div>
      <p
        style={{
          fontSize: 13,
          color: 'var(--ink-soft)',
          fontWeight: 700,
          margin: '6px 0 14px',
        }}
      >
        {copy.desc}
      </p>
      <Link to="/add" className="btn btn-primary">
        <Icon name="plus" size={16} stroke="#fff" /> Add a book
      </Link>
    </div>
  )
}

function ReadingList({ books }: { books: Book[] }) {
  return (
    <div style={{ display: 'grid', gap: 12 }}>
      {books.map((b, i) => {
        const pages = b.pageCount ?? 0
        const progress = b.progress ?? 0
        const pct = pages > 0 ? Math.round((progress / pages) * 100) : 0
        return (
          <Link
            key={b.id}
            to={`/book/${b.id}`}
            style={{
              cursor: 'pointer',
              display: 'flex',
              gap: 12,
              padding: 10,
              border: '2px dashed var(--ink-mute)',
              borderRadius: 14,
              textDecoration: 'none',
              color: 'inherit',
            }}
          >
            <BookCover book={b} size="sm" tilt={i % 2 ? -2 : 2} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <h3
                className="serif"
                style={{
                  fontSize: 16,
                  lineHeight: 1.1,
                  overflow: 'hidden',
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical',
                }}
              >
                {b.title}
              </h3>
              <div
                style={{
                  fontSize: 11,
                  fontWeight: 700,
                  color: 'var(--ink-mute)',
                  marginTop: 2,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {b.authors.join(', ') || 'unknown'}
              </div>
              {pages > 0 && (
                <div style={{ marginTop: 8 }}>
                  <div
                    style={{
                      fontSize: 10,
                      fontWeight: 800,
                      color: 'var(--ink-mute)',
                      marginBottom: 3,
                    }}
                  >
                    {progress} / {pages} pages · {pct}%
                  </div>
                  <div className="progress" style={{ height: 10 }}>
                    <div className="progress-fill" style={{ width: `${pct}%` }} />
                  </div>
                </div>
              )}
            </div>
          </Link>
        )
      })}
    </div>
  )
}

function CoverGrid({
  books,
  heading,
  countLabel,
}: {
  books: Book[]
  heading: string
  countLabel: string
}) {
  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 14,
          flexWrap: 'wrap',
        }}
      >
        <div className="serif" style={{ fontSize: 16, fontWeight: 700 }}>
          {heading}
        </div>
        <div className="chip">
          <Icon name="sparkle" size={12} /> {countLabel}
        </div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
        {books.map((b, i) => (
          <Link
            key={b.id}
            to={`/book/${b.id}`}
            style={{ textDecoration: 'none', color: 'inherit', textAlign: 'center' }}
          >
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 8 }}>
              <BookCover book={b} size="sm" tilt={((i % 3) - 1) * 2} />
            </div>
            <div
              className="serif"
              style={{
                fontSize: 12,
                fontWeight: 700,
                lineHeight: 1.15,
                overflow: 'hidden',
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
              }}
            >
              {b.title}
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}

function FinishedList({ books }: { books: Book[] }) {
  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 14,
          flexWrap: 'wrap',
        }}
      >
        <div className="serif" style={{ fontSize: 16, fontWeight: 700 }}>
          Books you conquered 🏆
        </div>
        <div className="chip chip-active">{books.length}</div>
      </div>
      <div style={{ display: 'grid', gap: 12 }}>
        {books.map((b, i) => (
          <Link
            key={b.id}
            to={`/book/${b.id}`}
            style={{
              cursor: 'pointer',
              display: 'flex',
              gap: 12,
              padding: 10,
              border: '2px solid var(--line)',
              borderRadius: 14,
              background: 'var(--bg-2)',
              textDecoration: 'none',
              color: 'inherit',
            }}
          >
            <BookCover book={b} size="sm" tilt={i % 2 ? -2 : 2} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'flex-start',
                  gap: 8,
                }}
              >
                <div style={{ minWidth: 0 }}>
                  <h3
                    className="serif"
                    style={{
                      fontSize: 15,
                      lineHeight: 1.1,
                      overflow: 'hidden',
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                    }}
                  >
                    {b.title}
                  </h3>
                  <div
                    style={{
                      fontSize: 11,
                      fontWeight: 700,
                      color: 'var(--ink-mute)',
                      marginTop: 2,
                    }}
                  >
                    {b.authors.join(', ') || 'unknown'}
                  </div>
                </div>
                <StarRating rating={b.rating} size={13} />
              </div>
              {b.notes && (
                <div
                  style={{
                    marginTop: 8,
                    padding: 8,
                    background: 'var(--paper)',
                    border: '2px dashed var(--ink-mute)',
                    borderRadius: 10,
                    fontSize: 12,
                    fontWeight: 600,
                    fontStyle: 'italic',
                    overflow: 'hidden',
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                  }}
                >
                  “{b.notes}”
                </div>
              )}
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
