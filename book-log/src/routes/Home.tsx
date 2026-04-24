import { Link } from 'react-router-dom'
import BookCover from '../components/BookCover'
import Icon from '../components/Icon'
import OwlMascot from '../components/OwlMascot'
import { useBooks } from '../hooks/useBooks'
import { useProfile } from '../lib/profile'
import { computeBadges, computeStats, YEAR_GOAL } from '../lib/stats'

function timeOfDayLabel(): string {
  const h = new Date().getHours()
  if (h < 5) return 'Late night reads'
  if (h < 12) return 'Good morning'
  if (h < 17) return 'Afternoon adventure'
  if (h < 21) return 'Evening cozy'
  return 'Bedtime story'
}

export default function Home() {
  const books = useBooks()
  const { profile } = useProfile()
  const loading = books === undefined
  const list = books ?? []
  const stats = computeStats(list)
  const reading = list.filter((b) => b.status === 'reading').slice(0, 3)
  const badges = computeBadges(stats, list)
  const freshBadge =
    badges.filter((b) => b.unlocked).slice(-1)[0] ||
    badges.find((b) => !b.unlocked)
  const goalDone = Math.min(stats.finishedThisYear, YEAR_GOAL)
  const displayName = profile?.name?.trim() || 'friend'

  return (
    <div className="paper-bg" style={{ minHeight: '100%', padding: '20px 16px 120px' }}>
      {/* Greeting */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 22 }}>
        <div className="float" style={{ flexShrink: 0 }}>
          <OwlMascot size={88} mood="reading" />
        </div>
        <div style={{ flex: 1, minWidth: 0, paddingTop: 4 }}>
          <div
            style={{
              fontSize: 11,
              fontWeight: 800,
              letterSpacing: '0.1em',
              textTransform: 'uppercase',
              color: 'var(--ink-mute)',
            }}
          >
            {timeOfDayLabel()}
          </div>
          <h1 className="serif" style={{ fontSize: 32, lineHeight: 1.05, marginTop: 2 }}>
            Hey, <span className="wavy">{displayName}!</span>
          </h1>
          <p
            style={{
              marginTop: 6,
              fontSize: 14,
              color: 'var(--ink-soft)',
              fontWeight: 700,
              margin: '6px 0 0',
            }}
          >
            Nice one, bookworm! Keep wiggling through those pages 🐛
          </p>
        </div>
      </div>

      {/* Stat row */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 22 }}>
        <div
          className="card rotate-sm"
          style={{ padding: 14, background: 'var(--accent-2)' }}
        >
          <div
            style={{
              fontSize: 10,
              fontWeight: 800,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
            }}
          >
            On the shelf
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 2 }}>
            <div className="serif" style={{ fontSize: 32, lineHeight: 1, fontWeight: 800 }}>
              {loading ? '–' : stats.total}
            </div>
            <div style={{ fontWeight: 800, fontSize: 12 }}>books 📚</div>
          </div>
        </div>
        <div
          className="card rotate-sm-r"
          style={{ padding: 14, background: 'var(--accent-3)', color: '#fff' }}
        >
          <div
            style={{
              fontSize: 10,
              fontWeight: 800,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
            }}
          >
            Reading now
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 2 }}>
            <div className="serif" style={{ fontSize: 32, lineHeight: 1, fontWeight: 800 }}>
              {loading ? '–' : stats.reading}
            </div>
            <div style={{ fontWeight: 800, fontSize: 12 }}>on the go 📖</div>
          </div>
        </div>
        <div
          className="card rotate-sm"
          style={{
            padding: 14,
            background: 'var(--accent-1)',
            color: '#fff',
            gridColumn: '1 / -1',
          }}
        >
          <div
            style={{
              fontSize: 10,
              fontWeight: 800,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
            }}
          >
            Books this year
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 2 }}>
            <div className="serif" style={{ fontSize: 32, lineHeight: 1, fontWeight: 800 }}>
              {loading ? '–' : goalDone}
            </div>
            <div style={{ fontWeight: 800, fontSize: 12 }}>/ {YEAR_GOAL} conquered 🔥</div>
          </div>
        </div>
      </div>

      {/* Currently reading */}
      <div style={{ marginBottom: 22 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'baseline',
            justifyContent: 'space-between',
            marginBottom: 10,
            gap: 8,
          }}
        >
          <h2 className="serif" style={{ fontSize: 22 }}>
            Right now you're reading…
          </h2>
          {reading.length > 0 && (
            <Link to="/shelves" className="chip">
              See all
            </Link>
          )}
        </div>
        {loading ? (
          <div className="card" style={{ padding: 18, color: 'var(--ink-mute)' }}>
            Loading your books…
          </div>
        ) : reading.length === 0 ? (
          <div className="card" style={{ padding: 18 }}>
            <div className="serif" style={{ fontSize: 18, marginBottom: 4 }}>
              No books in progress yet
            </div>
            <p style={{ fontSize: 14, color: 'var(--ink-soft)', fontWeight: 700, margin: 0 }}>
              Scan a book to start your first adventure.
            </p>
            <Link
              to="/add"
              className="btn btn-primary"
              style={{ marginTop: 12 }}
            >
              <Icon name="plus" size={16} stroke="#fff" /> Log a book
            </Link>
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 12 }}>
            {reading.map((b, i) => {
              const pages = b.pageCount ?? 0
              const progress = b.progress ?? 0
              const pct = pages > 0 ? Math.round((progress / pages) * 100) : 0
              return (
                <Link
                  key={b.id}
                  to={`/book/${b.id}`}
                  className="card"
                  style={{
                    padding: 14,
                    display: 'flex',
                    gap: 12,
                    textDecoration: 'none',
                    color: 'inherit',
                  }}
                >
                  <div className={i % 2 ? 'rotate-sm-r' : 'rotate-sm'}>
                    <BookCover book={b} size="sm" />
                  </div>
                  <div
                    style={{
                      flex: 1,
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between',
                      minWidth: 0,
                    }}
                  >
                    <div>
                      <div
                        className="serif"
                        style={{
                          fontSize: 16,
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
                      <div
                        style={{
                          fontSize: 11,
                          color: 'var(--ink-mute)',
                          fontWeight: 700,
                          marginTop: 2,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        by {b.authors.join(', ') || 'unknown'}
                      </div>
                    </div>
                    {pages > 0 ? (
                      <div style={{ marginTop: 6 }}>
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            fontSize: 11,
                            fontWeight: 800,
                            marginBottom: 3,
                          }}
                        >
                          <span>Page {progress}</span>
                          <span style={{ color: 'var(--accent-1)' }}>{pct}%</span>
                        </div>
                        <div className="progress" style={{ height: 10 }}>
                          <div
                            className="progress-fill"
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                      </div>
                    ) : (
                      <div
                        style={{
                          fontSize: 11,
                          color: 'var(--ink-mute)',
                          fontWeight: 700,
                          marginTop: 6,
                        }}
                      >
                        Tap to update your progress
                      </div>
                    )}
                  </div>
                </Link>
              )
            })}
          </div>
        )}
      </div>

      {/* Challenge + badge */}
      <div
        className="card"
        style={{
          padding: 18,
          background: 'var(--accent-3)',
          color: '#fff',
          marginBottom: 16,
          position: 'relative',
        }}
      >
        <div className="tape" />
        <div
          style={{
            fontSize: 10,
            fontWeight: 800,
            textTransform: 'uppercase',
            letterSpacing: '0.1em',
            opacity: 0.85,
          }}
        >
          Your {new Date().getFullYear()} challenge
        </div>
        <h2 className="serif" style={{ fontSize: 22, marginTop: 4, marginBottom: 10 }}>
          Read {YEAR_GOAL} books this year
        </h2>
        <div style={{ display: 'flex', gap: 3, flexWrap: 'wrap', marginBottom: 12 }}>
          {Array.from({ length: YEAR_GOAL }).map((_, i) => (
            <div
              key={i}
              style={{
                width: 14,
                height: 20,
                background: i < goalDone ? 'var(--accent-2)' : 'rgba(255,255,255,0.15)',
                border: `1.5px solid ${
                  i < goalDone ? 'var(--line)' : 'rgba(255,255,255,0.35)'
                }`,
                borderRadius: '2px 4px 4px 2px',
              }}
            />
          ))}
        </div>
        <Link to="/quest" className="btn btn-accent-2">
          See my quest <Icon name="arrow-right" size={14} />
        </Link>
      </div>

      {freshBadge && (
        <div
          className="card rotate-sm-r"
          style={{ padding: 16, background: 'var(--sticker-pink)' }}
        >
          <div
            style={{
              fontSize: 10,
              fontWeight: 800,
              textTransform: 'uppercase',
              letterSpacing: '0.1em',
            }}
          >
            {freshBadge.unlocked ? 'Fresh badge!' : 'Up next to unlock'}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 8 }}>
            <div
              className={freshBadge.unlocked ? 'sticker wobble' : 'sticker'}
              style={{
                width: 62,
                height: 62,
                background: freshBadge.unlocked
                  ? freshBadge.rule.colorVar
                  : 'var(--bg-2)',
                fontSize: 30,
                flexShrink: 0,
                filter: freshBadge.unlocked ? 'none' : 'grayscale(0.7)',
              }}
            >
              {freshBadge.rule.emoji}
            </div>
            <div style={{ minWidth: 0 }}>
              <h3 className="serif" style={{ fontSize: 18, lineHeight: 1.1 }}>
                {freshBadge.rule.name}
              </h3>
              <div style={{ fontSize: 12, fontWeight: 700, marginTop: 4 }}>
                {freshBadge.rule.desc}
                {!freshBadge.unlocked && freshBadge.label && (
                  <> — {freshBadge.label}</>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
