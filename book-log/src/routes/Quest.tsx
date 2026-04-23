import Icon from '../components/Icon'
import OwlMascot from '../components/OwlMascot'
import { useBooks } from '../hooks/useBooks'
import { computeStats, YEAR_GOAL } from '../lib/stats'
import BookCover from '../components/BookCover'

const MILESTONES: {
  at: number
  reward: string
  emoji: string
}[] = [
  { at: 5, reward: 'Five-Book Club sticker', emoji: '🖐️' },
  { at: 10, reward: 'Halfway Hero badge', emoji: '🏅' },
  { at: 15, reward: 'Library Legend status', emoji: '📚' },
  { at: 20, reward: 'Quest Complete trophy', emoji: '🏆' },
]

export default function Quest() {
  const books = useBooks() ?? []
  const stats = computeStats(books)
  const done = Math.min(stats.finishedThisYear, YEAR_GOAL)
  const percent = Math.round((done / YEAR_GOAL) * 100)

  const finishedRecent = books
    .filter((b) => b.status === 'read' && b.dateFinished)
    .sort((a, b) => (b.dateFinished ?? 0) - (a.dateFinished ?? 0))
    .slice(0, 4)

  const now = new Date()
  const year = now.getFullYear()
  const daysIn =
    Math.floor((now.getTime() - new Date(year, 0, 1).getTime()) / 86_400_000) + 1
  const pace = done / Math.max(1, daysIn)
  const projectedTotal = Math.min(YEAR_GOAL, Math.round(pace * 365))
  const projectionLabel =
    done === 0
      ? '—'
      : pace * 365 >= YEAR_GOAL
        ? monthOfDay(Math.round(YEAR_GOAL / pace))
        : `${projectedTotal} total`

  return (
    <div className="paper-bg" style={{ minHeight: '100%', padding: '20px 16px 120px' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: 16,
          gap: 10,
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div
            style={{
              fontSize: 11,
              fontWeight: 800,
              letterSpacing: '0.1em',
              textTransform: 'uppercase',
              color: 'var(--ink-mute)',
            }}
          >
            Your quest
          </div>
          <h1 className="serif" style={{ fontSize: 28, marginTop: 2, lineHeight: 1.05 }}>
            The {year} Reading Challenge
          </h1>
        </div>
      </div>

      {/* Main quest map */}
      <div
        className="card"
        style={{
          padding: 18,
          marginBottom: 18,
          background: 'var(--accent-3)',
          color: '#fff',
          position: 'relative',
        }}
      >
        <div className="tape" />
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
          <div className="float" style={{ flexShrink: 0 }}>
            <OwlMascot size={76} mood="reading" />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="serif" style={{ fontSize: 16, lineHeight: 1.25 }}>
              {done === 0 ? (
                <>
                  “Let’s get started! Finish a book to kick things off.”
                </>
              ) : percent >= 100 ? (
                <>“You did it! Quest complete! 🏆”</>
              ) : (
                <>
                  “You’re{' '}
                  <span
                    style={{
                      background: 'var(--accent-2)',
                      color: 'var(--ink)',
                      padding: '2px 8px',
                      borderRadius: 6,
                      fontWeight: 800,
                    }}
                  >
                    {percent}% through
                  </span>{' '}
                  your quest — keep going!”
                </>
              )}
            </div>
            <div style={{ fontSize: 12, fontWeight: 700, opacity: 0.85, marginTop: 4 }}>
              — Hootie, your reading buddy
            </div>
          </div>
        </div>

        {/* Trail — 4 rows × 5 cols on phone */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(5, 1fr)',
            gap: 10,
            marginTop: 18,
          }}
        >
          {Array.from({ length: YEAR_GOAL }).map((_, i) => {
            const isDone = i < done
            const isNext = i === done
            const isMilestone = [4, 9, 14, 19].includes(i)
            return (
              <div
                key={i}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                <div
                  style={{
                    width: isMilestone ? 36 : 28,
                    height: isMilestone ? 36 : 28,
                    borderRadius: '50%',
                    background: isDone
                      ? 'var(--accent-2)'
                      : isNext
                        ? 'var(--paper)'
                        : 'rgba(255,255,255,0.18)',
                    border: `2px solid ${
                      isDone || isNext ? 'var(--line)' : 'rgba(255,255,255,0.4)'
                    }`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontFamily: 'Fraunces, serif',
                    fontWeight: 800,
                    fontSize: isMilestone ? 15 : 11,
                    color: isDone
                      ? 'var(--ink)'
                      : isNext
                        ? 'var(--ink)'
                        : 'rgba(255,255,255,0.7)',
                    boxShadow: isDone || isNext ? 'var(--shadow-sm)' : 'none',
                  }}
                >
                  {isDone ? (isMilestone ? '⭐' : '✓') : i + 1}
                </div>
                {isMilestone && (
                  <div
                    style={{
                      fontSize: 8,
                      fontWeight: 800,
                      textTransform: 'uppercase',
                      letterSpacing: '0.05em',
                      whiteSpace: 'nowrap',
                      background: 'var(--accent-2)',
                      color: 'var(--ink)',
                      padding: '1px 4px',
                      borderRadius: 3,
                      border: '1.5px solid var(--line)',
                      transform: 'rotate(-5deg)',
                    }}
                  >
                    Book {i + 1}
                  </div>
                )}
              </div>
            )
          })}
        </div>

        {/* Stats */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: 8,
            marginTop: 18,
          }}
        >
          <QuestStat value={done} label="Done" />
          <QuestStat value={YEAR_GOAL - done} label="To go" />
          <QuestStat value={projectionLabel} label="Projected" />
        </div>
      </div>

      {/* Milestones */}
      <div className="card" style={{ padding: 18, marginBottom: 16 }}>
        <h2 className="serif" style={{ fontSize: 20, marginBottom: 12 }}>
          🏆 Milestone rewards
        </h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {MILESTONES.map((m) => {
            const unlocked = done >= m.at
            return (
              <div
                key={m.at}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: 10,
                  background: unlocked ? 'var(--sticker-green)' : 'var(--bg-2)',
                  border: '2px solid var(--line)',
                  borderRadius: 12,
                  opacity: unlocked ? 1 : 0.85,
                }}
              >
                <div
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: '50%',
                    background: unlocked ? 'var(--accent-2)' : 'var(--paper)',
                    border: '2px solid var(--line)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 20,
                    flexShrink: 0,
                  }}
                >
                  {m.emoji}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      fontSize: 11,
                      fontWeight: 800,
                      color: 'var(--ink-mute)',
                      textTransform: 'uppercase',
                      letterSpacing: '0.05em',
                    }}
                  >
                    At {m.at} books
                  </div>
                  <div className="serif" style={{ fontSize: 14, fontWeight: 700 }}>
                    {m.reward}
                  </div>
                </div>
                {unlocked && <Icon name="check" size={18} />}
              </div>
            )
          })}
        </div>
      </div>

      {/* Recent wins */}
      {finishedRecent.length > 0 && (
        <div
          className="card"
          style={{ padding: 18, background: 'var(--sticker-pink)' }}
        >
          <h2 className="serif" style={{ fontSize: 20, marginBottom: 12 }}>
            Your recent conquests
          </h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {finishedRecent.map((b, i) => (
              <div
                key={b.id}
                style={{
                  display: 'flex',
                  gap: 10,
                  alignItems: 'center',
                  padding: 8,
                  background: 'var(--paper)',
                  border: '2px solid var(--line)',
                  borderRadius: 10,
                }}
              >
                <div
                  className="serif"
                  style={{
                    width: 28,
                    height: 28,
                    background: 'var(--accent-2)',
                    border: '2px solid var(--line)',
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 12,
                    fontWeight: 800,
                    flexShrink: 0,
                  }}
                >
                  {finishedRecent.length - i}
                </div>
                <BookCover book={b} size="xs" />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    className="serif"
                    style={{
                      fontSize: 14,
                      fontWeight: 700,
                      lineHeight: 1.1,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {b.title}
                  </div>
                  <div
                    style={{
                      fontSize: 10,
                      fontWeight: 700,
                      color: 'var(--ink-mute)',
                    }}
                  >
                    {b.dateFinished ? relativeDays(b.dateFinished) : ''}
                    {b.pageCount ? ` · ${b.pageCount} pages` : ''}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function QuestStat({
  value,
  label,
}: {
  value: number | string
  label: string
}) {
  return (
    <div
      style={{
        background: 'rgba(255,255,255,0.15)',
        padding: 10,
        borderRadius: 12,
        border: '2px solid rgba(255,255,255,0.3)',
        textAlign: 'center',
      }}
    >
      <div className="serif" style={{ fontSize: 22, fontWeight: 800, lineHeight: 1 }}>
        {value}
      </div>
      <div style={{ fontSize: 10, fontWeight: 800, marginTop: 3 }}>{label}</div>
    </div>
  )
}

function monthOfDay(dayOfYear: number): string {
  const d = new Date(new Date().getFullYear(), 0, dayOfYear)
  return d.toLocaleString('en', { month: 'short' })
}

function relativeDays(ts: number): string {
  const days = Math.max(0, Math.floor((Date.now() - ts) / 86_400_000))
  if (days === 0) return 'today'
  if (days === 1) return 'yesterday'
  if (days < 30) return `${days} days ago`
  const months = Math.floor(days / 30)
  return `${months} month${months === 1 ? '' : 's'} ago`
}
