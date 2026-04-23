import { Link } from 'react-router-dom'
import type { Book } from '../lib/types'
import { STATUS_META } from '../lib/status'

interface BookCardProps {
  book: Book
  index?: number
}

export default function BookCard({ book, index = 0 }: BookCardProps) {
  const meta = STATUS_META[book.status]
  const tilt = index % 3 === 0 ? '-rotate-1' : index % 3 === 1 ? 'rotate-1' : ''

  return (
    <Link
      to={`/book/${book.id}`}
      className={`group flex transform flex-col overflow-hidden rounded-2xl border-4 ${meta.cardBorder} bg-white shadow-chunkySm transition hover:-translate-y-1 hover:shadow-chunky ${tilt}`}
    >
      <div className="relative aspect-[2/3] w-full overflow-hidden bg-gradient-to-br from-amber-100 to-violet-100">
        {book.coverUrl ? (
          <img
            src={book.coverUrl}
            alt={`Cover of ${book.title}`}
            loading="lazy"
            className="h-full w-full object-cover transition group-hover:scale-[1.03]"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center p-4 text-center text-4xl">
            📖
          </div>
        )}
        <span
          className={`absolute left-2 top-2 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold ${meta.cardBadge}`}
        >
          <span aria-hidden>{meta.emoji}</span>
          {meta.label}
        </span>
        {book.rating && (
          <span className="absolute bottom-2 right-2 rounded-full bg-white/95 px-2 py-0.5 text-xs font-bold text-amber-600 shadow">
            {'★'.repeat(book.rating)}
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-1 p-3">
        <h3 className="line-clamp-2 text-sm font-bold leading-tight text-indigo-950">
          {book.title}
        </h3>
        <p className="line-clamp-1 text-xs font-medium text-indigo-600">
          {book.authors.join(', ')}
        </p>
      </div>
    </Link>
  )
}
