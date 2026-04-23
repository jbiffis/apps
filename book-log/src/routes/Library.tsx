import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import BookGrid from '../components/BookGrid'
import EmptyState from '../components/EmptyState'
import SearchBar, { type StatusFilter } from '../components/SearchBar'
import { useBooks } from '../hooks/useBooks'

export default function Library() {
  const books = useBooks()
  const loading = books === undefined

  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<StatusFilter>('all')

  const filtered = useMemo(() => {
    if (!books) return []
    const q = query.trim().toLowerCase()
    return books.filter((b) => {
      if (filter !== 'all' && b.status !== filter) return false
      if (!q) return true
      const hay = [
        b.title,
        b.authors.join(' '),
        b.tags.join(' '),
        b.publisher ?? '',
      ]
        .join(' ')
        .toLowerCase()
      return hay.includes(q)
    })
  }, [books, query, filter])

  const total = books?.length ?? 0
  const shown = filtered.length

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-6">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">My Library</h1>
          <p className="text-sm text-slate-500">
            {loading
              ? ' '
              : total === 0
                ? '0 books'
                : shown === total
                  ? `${total} ${total === 1 ? 'book' : 'books'}`
                  : `${shown} of ${total}`}
          </p>
        </div>
        <div className="flex gap-2">
          <Link
            to="/stats"
            className="hidden rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 sm:inline-flex sm:items-center"
          >
            Stats
          </Link>
          <Link
            to="/add"
            className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Add
          </Link>
          <Link
            to="/scan"
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            Scan
          </Link>
        </div>
      </header>

      {total > 0 && (
        <SearchBar
          query={query}
          onQueryChange={setQuery}
          filter={filter}
          onFilterChange={setFilter}
        />
      )}

      {loading ? (
        <EmptyState message="Loading…" />
      ) : total === 0 ? (
        <EmptyState message="No books yet — tap Add or Scan to add your first one." />
      ) : shown === 0 ? (
        <EmptyState message="No books match your filter." />
      ) : (
        <BookGrid books={filtered} />
      )}
    </div>
  )
}
