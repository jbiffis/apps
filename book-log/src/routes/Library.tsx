import { Link } from 'react-router-dom'
import BookGrid from '../components/BookGrid'
import EmptyState from '../components/EmptyState'
import type { Book } from '../lib/types'

const DUMMY_BOOKS: Book[] = [
  {
    id: '9780261103573',
    isbn13: '9780261103573',
    title: 'The Fellowship of the Ring',
    authors: ['J.R.R. Tolkien'],
    coverUrl: 'https://covers.openlibrary.org/b/isbn/9780261103573-L.jpg',
    status: 'read',
    rating: 5,
    dateAdded: Date.now(),
    tags: ['fantasy'],
  },
  {
    id: '9780441172719',
    isbn13: '9780441172719',
    title: 'Dune',
    authors: ['Frank Herbert'],
    coverUrl: 'https://covers.openlibrary.org/b/isbn/9780441172719-L.jpg',
    status: 'reading',
    dateAdded: Date.now(),
    tags: ['sci-fi'],
  },
  {
    id: '9780140449136',
    isbn13: '9780140449136',
    title: 'Crime and Punishment',
    authors: ['Fyodor Dostoevsky'],
    coverUrl: 'https://covers.openlibrary.org/b/isbn/9780140449136-L.jpg',
    status: 'want-to-read',
    dateAdded: Date.now(),
    tags: ['classic'],
  },
]

export default function Library() {
  const books = DUMMY_BOOKS

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-6">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">My Library</h1>
          <p className="text-sm text-slate-500">
            {books.length} {books.length === 1 ? 'book' : 'books'}
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
      {books.length === 0 ? (
        <EmptyState message="No books yet — tap Scan to add your first one." />
      ) : (
        <BookGrid books={books} />
      )}
    </div>
  )
}
