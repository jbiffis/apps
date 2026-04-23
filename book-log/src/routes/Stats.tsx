import { useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useBooks } from '../hooks/useBooks'
import { exportLibrary, importLibrary } from '../lib/exportImport'

export default function Stats() {
  const books = useBooks()
  const fileRef = useRef<HTMLInputElement>(null)
  const [importMsg, setImportMsg] = useState<string | null>(null)

  const stats = useMemo(() => {
    if (!books) return null
    const byStatus = { read: 0, reading: 0, 'want-to-read': 0 }
    const byYear: Record<number, number> = {}
    const authorCount: Record<string, number> = {}
    let totalPages = 0
    let ratedCount = 0
    let ratingSum = 0

    for (const b of books) {
      byStatus[b.status]++
      if (b.status === 'read') {
        if (b.pageCount) totalPages += b.pageCount
        if (b.dateFinished) {
          const year = new Date(b.dateFinished).getFullYear()
          byYear[year] = (byYear[year] ?? 0) + 1
        }
      }
      for (const a of b.authors) {
        authorCount[a] = (authorCount[a] ?? 0) + 1
      }
      if (b.rating) {
        ratedCount++
        ratingSum += b.rating
      }
    }

    const topAuthors = Object.entries(authorCount)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)

    const years = Object.entries(byYear)
      .map(([y, n]) => ({ year: Number(y), count: n }))
      .sort((a, b) => b.year - a.year)

    return {
      total: books.length,
      byStatus,
      byYear: years,
      topAuthors,
      totalPages,
      avgRating: ratedCount > 0 ? ratingSum / ratedCount : null,
    }
  }, [books])

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

  const readCount = stats?.byStatus.read ?? 0
  const headline =
    readCount === 0
      ? 'Finish a book to see stats!'
      : readCount < 5
        ? 'Great start!'
        : readCount < 20
          ? 'You’re a reader!'
          : readCount < 50
            ? 'Bookworm status 🐛'
            : 'Reading legend 🏆'

  return (
    <div className="mx-auto w-full max-w-3xl px-4 pb-12 pt-6">
      <div className="mb-4">
        <Link
          to="/"
          className="inline-flex items-center gap-1 rounded-full border-2 border-brand-200 bg-white px-3 py-1.5 text-sm font-bold text-brand-700 hover:bg-brand-50"
        >
          ← Bookshelf
        </Link>
      </div>
      <h1 className="text-3xl font-bold text-indigo-950">
        My Stats <span aria-hidden>📊</span>
      </h1>
      <p className="mt-1 text-base font-medium text-indigo-700">{headline}</p>

      {stats && (
        <>
          <section className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Stat label="Total" value={stats.total} emoji="📚" color="brand" />
            <Stat
              label="Finished"
              value={stats.byStatus.read}
              emoji="⭐"
              color="emerald"
            />
            <Stat
              label="Reading"
              value={stats.byStatus.reading}
              emoji="📖"
              color="amber"
            />
            <Stat
              label="On my list"
              value={stats.byStatus['want-to-read']}
              emoji="🔖"
              color="violet"
            />
          </section>

          <section className="mt-4 grid grid-cols-2 gap-3">
            <Stat
              label="Pages read"
              value={stats.totalPages.toLocaleString()}
              emoji="📄"
              color="sky"
            />
            <Stat
              label="Avg rating"
              value={stats.avgRating === null ? '—' : stats.avgRating.toFixed(1)}
              emoji="🌟"
              color="amber"
            />
          </section>

          {stats.byYear.length > 0 && (
            <section className="mt-8">
              <h2 className="mb-2 text-sm font-bold uppercase tracking-wide text-indigo-600">
                Books finished by year
              </h2>
              <ul className="space-y-2">
                {stats.byYear.map((y) => {
                  const max = Math.max(...stats.byYear.map((x) => x.count))
                  const pct = Math.max(8, (y.count / max) * 100)
                  return (
                    <li
                      key={y.year}
                      className="rounded-2xl border-2 border-brand-200 bg-white p-3 shadow-chunkySm"
                    >
                      <div className="flex items-baseline justify-between">
                        <span className="text-base font-bold text-indigo-900">
                          {y.year}
                        </span>
                        <span className="text-lg font-bold text-brand-700">
                          {y.count}
                        </span>
                      </div>
                      <div className="mt-1 h-3 w-full overflow-hidden rounded-full bg-brand-100">
                        <div
                          className="h-full rounded-full bg-gradient-to-r from-amber-400 to-pink-400"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </li>
                  )
                })}
              </ul>
            </section>
          )}

          {stats.topAuthors.length > 0 && (
            <section className="mt-8">
              <h2 className="mb-2 text-sm font-bold uppercase tracking-wide text-indigo-600">
                Your favorite authors
              </h2>
              <ul className="space-y-2">
                {stats.topAuthors.map(([name, n], i) => (
                  <li
                    key={name}
                    className="flex items-center gap-3 rounded-2xl border-2 border-brand-200 bg-white px-3 py-2 shadow-chunkySm"
                  >
                    <span
                      className="text-2xl"
                      aria-hidden
                    >{['🥇', '🥈', '🥉', '🏅', '🏅'][i]}</span>
                    <span className="flex-1 truncate font-semibold text-indigo-900">
                      {name}
                    </span>
                    <span className="rounded-full bg-brand-100 px-2 py-0.5 text-xs font-bold text-brand-700">
                      {n} {n === 1 ? 'book' : 'books'}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      <section className="mt-10">
        <h2 className="text-sm font-bold uppercase tracking-wide text-indigo-600">
          Save a backup
        </h2>
        <p className="mt-1 text-sm font-medium text-indigo-700">
          Download your whole bookshelf as a file (ask a grown-up to help save
          it somewhere safe).
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            onClick={exportLibrary}
            className="rounded-full bg-brand-600 px-4 py-2 text-sm font-bold text-white shadow-chunkySm hover:bg-brand-500"
          >
            💾 Download backup
          </button>
          <button
            onClick={onImportClick}
            className="rounded-full border-2 border-brand-200 bg-white px-4 py-2 text-sm font-bold text-brand-700 hover:bg-brand-50"
          >
            📥 Load backup
          </button>
          <input
            ref={fileRef}
            type="file"
            accept="application/json,.json"
            className="hidden"
            onChange={onFileChange}
          />
        </div>
        {importMsg && (
          <p className="mt-2 rounded-xl bg-brand-50 px-3 py-2 text-sm font-semibold text-brand-800">
            {importMsg}
          </p>
        )}
      </section>
    </div>
  )
}

function Stat({
  label,
  value,
  emoji,
  color,
}: {
  label: string
  value: number | string
  emoji: string
  color: 'brand' | 'emerald' | 'amber' | 'violet' | 'sky'
}) {
  const colors: Record<typeof color, string> = {
    brand: 'border-brand-300 bg-brand-50 text-brand-800',
    emerald: 'border-emerald-300 bg-emerald-50 text-emerald-800',
    amber: 'border-amber-300 bg-amber-50 text-amber-800',
    violet: 'border-violet-300 bg-violet-50 text-violet-800',
    sky: 'border-sky-300 bg-sky-50 text-sky-800',
  }
  return (
    <div
      className={`rounded-2xl border-2 px-3 py-3 shadow-chunkySm ${colors[color]}`}
    >
      <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wide">
        <span aria-hidden>{emoji}</span> {label}
      </div>
      <div className="mt-1 text-3xl font-bold">{value}</div>
    </div>
  )
}
