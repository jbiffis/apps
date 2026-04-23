import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import BookCover from '../components/BookCover'
import Icon from '../components/Icon'
import PaperScraps from '../components/PaperScraps'
import { useBooks } from '../hooks/useBooks'
import { exportLibrary, importLibrary } from '../lib/exportImport'
import { computeBadges, computeStats } from '../lib/stats'

export default function Profile() {
  const books = useBooks() ?? []
  const stats = computeStats(books)
  const badges = computeBadges(stats, books)
  const unlocked = badges.filter((b) => b.unlocked)
  const locked = badges.filter((b) => !b.unlocked)
  const fives = books.filter((b) => b.rating === 5)

  const fileRef = useRef<HTMLInputElement>(null)
  const [importMsg, setImportMsg] = useState<string | null>(null)

  async function onImportClick() {
    fileRef.current?.click()
  }

  async function onFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const { imported, skipped } = await importLibrary(file)
      setImportMsg(
        `Added ${imported} book${imported === 1 ? '' : 's'}${
          skipped > 0 ? ` (${skipped} already on your shelf).` : '!'
        }`,
      )
    } catch (err) {
      setImportMsg(err instanceof Error ? err.message : 'Import failed.')
    } finally {
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  return (
    <div className="paper-bg" style={{ minHeight: '100%', padding: '20px 16px 120px' }}>
      {/* Hero */}
      <div
        className="card"
        style={{
          padding: 18,
          marginBottom: 18,
          background: 'var(--accent-3)',
          color: '#fff',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <PaperScraps />
        <div
          style={{
            display: 'flex',
            gap: 14,
            alignItems: 'center',
            position: 'relative',
          }}
        >
          <div
            style={{
              width: 82,
              height: 82,
              borderRadius: '50%',
              background: 'var(--accent-2)',
              border: '3px solid var(--line)',
              boxShadow: 'var(--shadow)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 44,
              flexShrink: 0,
            }}
          >
            🦊
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                fontSize: 10,
                fontWeight: 800,
                textTransform: 'uppercase',
                letterSpacing: '0.1em',
                opacity: 0.85,
              }}
            >
              Your clubhouse profile
            </div>
            <h1 className="serif" style={{ fontSize: 24, lineHeight: 1.05, marginTop: 2 }}>
              Bookworm
            </h1>
            <div
              style={{
                fontSize: 12,
                fontWeight: 700,
                opacity: 0.9,
                marginTop: 2,
              }}
            >
              Level {Math.max(1, Math.floor(stats.finished / 3) + 1)} reader
            </div>
          </div>
        </div>
      </div>

      {/* Stats */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, 1fr)',
          gap: 10,
          marginBottom: 18,
        }}
      >
        {[
          {
            num: stats.finished,
            label: 'Books finished',
            color: 'var(--accent-1)',
            rot: -1.5,
          },
          {
            num: stats.totalPagesRead.toLocaleString(),
            label: 'Pages turned',
            color: 'var(--accent-2)',
            rot: 1.5,
          },
          {
            num: unlocked.length,
            label: 'Badges earned',
            color: 'var(--accent-3)',
            rot: -1.5,
          },
          {
            num: stats.avgRating === null ? '—' : stats.avgRating.toFixed(1),
            label: 'Avg stars',
            color: 'var(--accent-4)',
            rot: 1.5,
          },
        ].map((s, i) => (
          <div
            key={i}
            className="card"
            style={{
              padding: 12,
              background: s.color,
              color: '#fff',
              transform: `rotate(${s.rot}deg)`,
              textAlign: 'center',
            }}
          >
            <div
              className="serif"
              style={{ fontSize: 26, fontWeight: 800, lineHeight: 1 }}
            >
              {s.num}
            </div>
            <div style={{ fontSize: 11, fontWeight: 800, marginTop: 3 }}>
              {s.label}
            </div>
          </div>
        ))}
      </div>

      {/* Badge wall */}
      <div className="card" style={{ padding: 18, marginBottom: 18, position: 'relative' }}>
        <div className="tape tape-pink" />
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 12,
          }}
        >
          <div style={{ minWidth: 0 }}>
            <h2 className="serif" style={{ fontSize: 20 }}>
              Badge collection
            </h2>
            <div
              style={{
                fontSize: 11,
                color: 'var(--ink-mute)',
                fontWeight: 700,
                marginTop: 2,
              }}
            >
              Stickers on your reading quest
            </div>
          </div>
          <div className="chip chip-active">
            <Icon name="sparkle" size={12} /> {unlocked.length}/{badges.length}
          </div>
        </div>

        {unlocked.length > 0 && (
          <>
            <SectionLabel>Earned</SectionLabel>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: 14,
                marginBottom: 18,
              }}
            >
              {unlocked.map((b, i) => (
                <div key={b.rule.id} style={{ textAlign: 'center' }}>
                  <div
                    className={i % 2 ? 'rotate-sm-r' : 'rotate-sm'}
                    style={{
                      display: 'flex',
                      justifyContent: 'center',
                      marginBottom: 6,
                    }}
                  >
                    <div
                      className="sticker"
                      style={{
                        width: 64,
                        height: 64,
                        background: b.rule.colorVar,
                        fontSize: 28,
                        boxShadow: 'var(--shadow)',
                      }}
                    >
                      {b.rule.emoji}
                    </div>
                  </div>
                  <div
                    className="serif"
                    style={{ fontSize: 12, fontWeight: 700, lineHeight: 1.15 }}
                  >
                    {b.rule.name}
                  </div>
                  <div
                    style={{
                      fontSize: 10,
                      color: 'var(--ink-mute)',
                      fontWeight: 700,
                      marginTop: 2,
                      lineHeight: 1.2,
                    }}
                  >
                    {b.rule.desc}
                  </div>
                </div>
              ))}
            </div>
          </>
        )}

        {locked.length > 0 && (
          <>
            <SectionLabel>Still to unlock</SectionLabel>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: 14,
              }}
            >
              {locked.map((b) => (
                <div
                  key={b.rule.id}
                  style={{ textAlign: 'center', opacity: 0.7 }}
                >
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'center',
                      marginBottom: 6,
                    }}
                  >
                    <div
                      className="sticker"
                      style={{
                        width: 64,
                        height: 64,
                        background: 'var(--bg-2)',
                        fontSize: 28,
                        filter: 'grayscale(1)',
                      }}
                    >
                      {b.rule.emoji}
                    </div>
                  </div>
                  <div
                    className="serif"
                    style={{ fontSize: 12, fontWeight: 700, lineHeight: 1.15 }}
                  >
                    {b.rule.name}
                  </div>
                  <div
                    style={{
                      fontSize: 10,
                      color: 'var(--ink-mute)',
                      fontWeight: 700,
                      marginTop: 2,
                      lineHeight: 1.2,
                    }}
                  >
                    {b.rule.desc}
                  </div>
                  {b.label && (
                    <div
                      className="chip"
                      style={{ marginTop: 4, fontSize: 9, padding: '2px 6px' }}
                    >
                      {b.label}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      {/* 5-star squad */}
      {fives.length > 0 && (
        <div
          className="card"
          style={{
            padding: 16,
            background: 'var(--sticker-yellow)',
            marginBottom: 18,
          }}
        >
          <h2 className="serif" style={{ fontSize: 18, marginBottom: 10 }}>
            ⭐ Your 5-star squad
          </h2>
          <div
            style={{
              display: 'flex',
              gap: 14,
              overflowX: 'auto',
              paddingBottom: 6,
            }}
            className="no-scrollbar"
          >
            {fives.map((b, i) => (
              <Link
                key={b.id}
                to={`/book/${b.id}`}
                style={{
                  textDecoration: 'none',
                  color: 'inherit',
                  textAlign: 'center',
                  flexShrink: 0,
                  width: 96,
                }}
              >
                <BookCover book={b} size="sm" tilt={((i % 3) - 1) * 2} />
                <div
                  className="serif"
                  style={{
                    fontSize: 12,
                    fontWeight: 700,
                    marginTop: 6,
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
      )}

      {/* Backup */}
      <div className="card" style={{ padding: 18 }}>
        <h2 className="serif" style={{ fontSize: 18 }}>
          Save a backup
        </h2>
        <p
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--ink-soft)',
            margin: '4px 0 12px',
          }}
        >
          Download your whole bookshelf as a file (ask a grown-up to help save it somewhere safe).
        </p>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button onClick={exportLibrary} className="btn btn-primary">
            <Icon name="arrow-right" size={14} stroke="#fff" /> Download backup
          </button>
          <button onClick={onImportClick} className="btn">
            <Icon name="arrow-left" size={14} /> Load backup
          </button>
          <input
            ref={fileRef}
            type="file"
            accept="application/json,.json"
            className="hidden"
            style={{ display: 'none' }}
            onChange={onFileChange}
          />
        </div>
        {importMsg && (
          <div
            className="chip"
            style={{ marginTop: 10, background: 'var(--sticker-green)' }}
          >
            {importMsg}
          </div>
        )}
      </div>
    </div>
  )
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
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
      {children}
    </div>
  )
}
