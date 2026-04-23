import { Link } from 'react-router-dom'
import BookGrid from '../components/BookGrid'
import EmptyState from '../components/EmptyState'
import { useBooks } from '../hooks/useBooks'

export default function Library() {
  const books = useBooks()
  const loading = books === undefined
  const count = books?.length ?? 0

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-6">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">My Library</h1>
          <p className="text-sm text-slate-500">
            {loading ? ' ' : `${count} ${count === 1 ? 'book' : 'books'}`}
          </p>
        </div>
        <div className="flex gap-2">
          <Link
            to="/add"
            className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Add manually
          </Link>
          <Link
            to="/scan"
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            Scan
          </Link>
        </div>
      </header>
      {loading ? (
        <EmptyState message="Loading…" />
      ) : count === 0 ? (
        <EmptyState message="No books yet — tap Add manually or Scan to add your first one." />
      ) : (
        <BookGrid books={books!} />
      )}
    </div>
  )
}
