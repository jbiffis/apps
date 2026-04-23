import type { Book } from '../lib/types'
import BookCard from './BookCard'

interface BookGridProps {
  books: Book[]
}

export default function BookGrid({ books }: BookGridProps) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
      {books.map((book, i) => (
        <BookCard key={book.id} book={book} index={i} />
      ))}
    </div>
  )
}
