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
      setImportMsg(`Imported ${imported}, skipped ${skipped} (duplicates or invalid).`)
    } catch (err) {
      setImportMsg(err instanceof Error ? err.message : 'Import failed.')
    } finally {
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-6">
      <div className="mb-4">
        <Link to="/" className="text-sm text-slate-600 hover:underline">
          ← Back to library
        </Link>
      </div>
      <h1 className="text-2xl font-bold text-slate-900">Stats &amp; backup</h1>

      {stats && (
        <>
          <section className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Stat label="Total" value={stats.total} />
            <Stat label="Read" value={stats.byStatus.read} />
            <Stat label="Reading" value={stats.byStatus.reading} />
            <Stat label="Want to read" value={stats.byStatus['want-to-read']} />
          </section>

          <section className="mt-6 grid grid-cols-2 gap-3">
            <Stat
              label="Pages read"
              value={stats.totalPages.toLocaleString()}
            />
            <Stat
              label="Avg rating"
              value={
                stats.avgRating === null ? '—' : stats.avgRating.toFixed(1)
              }
            />
          </section>

          {stats.byYear.length > 0 && (
            <section className="mt-8">
              <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
                Books read by year
              </h2>
              <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
                {stats.byYear.map((y) => (
                  <li
                    key={y.year}
                    className="flex items-center justify-between px-3 py-2 text-sm"
                  >
                    <span className="text-slate-700">{y.year}</span>
                    <span className="font-medium text-slate-900">
                      {y.count}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {stats.topAuthors.length > 0 && (
            <section className="mt-8">
              <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
                Top authors
              </h2>
              <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
                {stats.topAuthors.map(([name, n]) => (
                  <li
                    key={name}
                    className="flex items-center justify-between px-3 py-2 text-sm"
                  >
                    <span className="truncate text-slate-700">{name}</span>
                    <span className="font-medium text-slate-900">{n}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      <section className="mt-10 border-t border-slate-200 pt-6">
        <h2 className="text-sm font-medium uppercase tracking-wide text-slate-500">
          Backup
        </h2>
        <p className="mt-1 text-sm text-slate-500">
          Export your library to a JSON file, or import a previous export.
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            onClick={exportLibrary}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            Export JSON
          </button>
          <button
            onClick={onImportClick}
            className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Import JSON
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
          <p className="mt-2 text-sm text-slate-600">{importMsg}</p>
        )}
      </section>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-3">
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold text-slate-900">{value}</dd>
    </div>
  )
}
