import { Link } from 'react-router-dom'
import type { Book } from '../lib/types'

interface BookCardProps {
  book: Book
}

const statusLabel: Record<Book['status'], string> = {
  read: 'Read',
  reading: 'Reading',
  'want-to-read': 'Want to read',
}

const statusColor: Record<Book['status'], string> = {
  read: 'bg-emerald-100 text-emerald-800',
  reading: 'bg-amber-100 text-amber-800',
  'want-to-read': 'bg-slate-200 text-slate-700',
}

export default function BookCard({ book }: BookCardProps) {
  return (
    <Link
      to={`/book/${book.id}`}
      className="group flex flex-col overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm transition hover:shadow-md"
    >
      <div className="aspect-[2/3] w-full overflow-hidden bg-slate-100">
        {book.coverUrl ? (
          <img
            src={book.coverUrl}
            alt={`Cover of ${book.title}`}
            loading="lazy"
            className="h-full w-full object-cover transition group-hover:scale-[1.02]"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center p-4 text-center text-sm text-slate-400">
            No cover
          </div>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-1 p-3">
        <h3 className="line-clamp-2 text-sm font-semibold text-slate-900">
          {book.title}
        </h3>
        <p className="line-clamp-1 text-xs text-slate-500">
          {book.authors.join(', ')}
        </p>
        <span
          className={`mt-1 inline-flex w-fit rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ${statusColor[book.status]}`}
        >
          {statusLabel[book.status]}
        </span>
      </div>
    </Link>
  )
}
