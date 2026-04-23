import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import BookGrid from '../components/BookGrid'
import EmptyState from '../components/EmptyState'
import SearchBar, { type StatusFilter } from '../components/SearchBar'
import { useBooks } from '../hooks/useBooks'

function countReadThisYear(books: { status: string; dateFinished?: number }[]) {
  const year = new Date().getFullYear()
  return books.filter(
    (b) =>
      b.status === 'read' &&
      b.dateFinished &&
      new Date(b.dateFinished).getFullYear() === year,
  ).length
}

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
  const finishedThisYear = books ? countReadThisYear(books) : 0

  return (
    <div className="mx-auto w-full max-w-6xl px-4 pb-24 pt-6">
      <header className="mb-6">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-4xl font-bold tracking-tight text-indigo-950">
              My Bookshelf <span aria-hidden>📚</span>
            </h1>
            <p className="mt-1 text-base font-medium text-indigo-700">
              {loading
                ? 'Loading your books…'
                : total === 0
                  ? 'You haven’t added any books yet!'
                  : finishedThisYear > 0
                    ? `You’ve finished ${finishedThisYear} ${
                        finishedThisYear === 1 ? 'book' : 'books'
                      } this year — keep going! 🎉`
                    : `${total} ${total === 1 ? 'book' : 'books'} on your shelf`}
            </p>
          </div>
          <Link
            to="/stats"
            className="inline-flex items-center gap-1 rounded-full border-2 border-brand-200 bg-white px-4 py-2 text-sm font-bold text-brand-700 shadow-chunkySm hover:bg-brand-50"
          >
            <span aria-hidden>📊</span> Stats
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
        <EmptyState message="Loading your books…" emoji="⏳" />
      ) : total === 0 ? (
        <EmptyState
          message="Tap the big button to scan your first book!"
          emoji="📖"
        />
      ) : shown === 0 ? (
        <EmptyState message="No books match that search." emoji="🤔" />
      ) : (
        <BookGrid books={filtered} />
      )}

      {/* Floating action buttons */}
      <div className="fixed bottom-4 left-1/2 z-40 flex -translate-x-1/2 gap-3">
        <Link
          to="/add"
          className="inline-flex items-center gap-2 rounded-full border-4 border-brand-200 bg-white px-5 py-3 text-base font-bold text-brand-700 shadow-chunky hover:-translate-y-0.5 hover:bg-brand-50"
        >
          <span aria-hidden>✏️</span> Type ISBN
        </Link>
        <Link
          to="/scan"
          className="inline-flex items-center gap-2 rounded-full bg-brand-600 px-6 py-3 text-base font-bold text-white shadow-chunky hover:-translate-y-0.5 hover:bg-brand-500"
        >
          <span aria-hidden>📷</span> Scan a book!
        </Link>
      </div>
    </div>
  )
}
