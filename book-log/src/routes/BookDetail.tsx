import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteBook, updateBook } from '../lib/db'
import { useBook } from '../hooks/useBooks'
import type { Book, ReadingStatus } from '../lib/types'

const STATUSES: { value: ReadingStatus; label: string }[] = [
  { value: 'want-to-read', label: 'Want to read' },
  { value: 'reading', label: 'Reading' },
  { value: 'read', label: 'Read' },
]

export default function BookDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const book = useBook(id)

  const [notes, setNotes] = useState('')
  const [tagsText, setTagsText] = useState('')
  const [notesDirty, setNotesDirty] = useState(false)
  const [tagsDirty, setTagsDirty] = useState(false)

  useEffect(() => {
    if (book) {
      if (!notesDirty) setNotes(book.notes ?? '')
      if (!tagsDirty) setTagsText(book.tags.join(', '))
    }
  }, [book, notesDirty, tagsDirty])

  if (book === undefined) {
    return (
      <div className="mx-auto w-full max-w-3xl px-4 py-6 text-sm text-slate-500">
        Loading…
      </div>
    )
  }

  if (book === null) {
    return (
      <div className="mx-auto w-full max-w-3xl px-4 py-6">
        <Link to="/" className="text-sm text-slate-600 hover:underline">
          ← Back to library
        </Link>
        <p className="mt-4 text-slate-700">Book not found.</p>
      </div>
    )
  }

  async function setStatus(status: ReadingStatus) {
    if (!book) return
    const changes: Partial<Book> = { status }
    if (status === 'read' && !book.dateFinished) {
      changes.dateFinished = Date.now()
    }
    await updateBook(book.id, changes)
  }

  async function setRating(rating: Book['rating']) {
    if (!book) return
    await updateBook(book.id, { rating })
  }

  async function saveNotes() {
    if (!book) return
    await updateBook(book.id, { notes: notes.trim() || undefined })
    setNotesDirty(false)
  }

  async function saveTags() {
    if (!book) return
    const tags = tagsText
      .split(',')
      .map((t) => t.trim())
      .filter(Boolean)
    await updateBook(book.id, { tags })
    setTagsDirty(false)
  }

  async function onDelete() {
    if (!book) return
    if (!confirm(`Delete "${book.title}" from your library?`)) return
    await deleteBook(book.id)
    navigate('/')
  }

  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-6">
      <div className="mb-4">
        <Link to="/" className="text-sm text-slate-600 hover:underline">
          ← Back to library
        </Link>
      </div>

      <div className="flex flex-col gap-6 sm:flex-row">
        <div className="w-full sm:w-48">
          {book.coverUrl ? (
            <img
              src={book.coverUrl}
              alt={`Cover of ${book.title}`}
              className="w-full rounded-lg bg-slate-100 object-cover shadow"
            />
          ) : (
            <div className="flex aspect-[2/3] w-full items-center justify-center rounded-lg bg-slate-100 text-sm text-slate-400">
              No cover
            </div>
          )}
        </div>

        <div className="flex-1">
          <h1 className="text-2xl font-bold text-slate-900">{book.title}</h1>
          <p className="mt-1 text-slate-600">{book.authors.join(', ')}</p>
          <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-sm text-slate-500">
            {book.publisher && (
              <>
                <dt>Publisher</dt>
                <dd className="text-slate-700">{book.publisher}</dd>
              </>
            )}
            {book.publishDate && (
              <>
                <dt>Published</dt>
                <dd className="text-slate-700">{book.publishDate}</dd>
              </>
            )}
            {book.pageCount && (
              <>
                <dt>Pages</dt>
                <dd className="text-slate-700">{book.pageCount}</dd>
              </>
            )}
            <dt>ISBN</dt>
            <dd className="font-mono text-slate-700">{book.isbn13}</dd>
          </dl>
        </div>
      </div>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Status
        </h2>
        <div className="flex flex-wrap gap-2">
          {STATUSES.map((s) => (
            <button
              key={s.value}
              onClick={() => setStatus(s.value)}
              className={
                book.status === s.value
                  ? 'rounded-full bg-slate-900 px-3 py-1 text-sm font-medium text-white'
                  : 'rounded-full border border-slate-300 bg-white px-3 py-1 text-sm font-medium text-slate-700 hover:bg-slate-50'
              }
            >
              {s.label}
            </button>
          ))}
        </div>
      </section>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Rating
        </h2>
        <div className="flex items-center gap-1">
          {[1, 2, 3, 4, 5].map((n) => {
            const active = (book.rating ?? 0) >= n
            return (
              <button
                key={n}
                onClick={() =>
                  setRating(book.rating === n ? undefined : (n as 1 | 2 | 3 | 4 | 5))
                }
                aria-label={`${n} star${n > 1 ? 's' : ''}`}
                className={
                  active
                    ? 'text-2xl text-amber-500'
                    : 'text-2xl text-slate-300 hover:text-amber-400'
                }
              >
                ★
              </button>
            )
          })}
          {book.rating && (
            <button
              onClick={() => setRating(undefined)}
              className="ml-2 text-xs text-slate-500 hover:underline"
            >
              clear
            </button>
          )}
        </div>
      </section>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Notes
        </h2>
        <textarea
          value={notes}
          onChange={(e) => {
            setNotes(e.target.value)
            setNotesDirty(true)
          }}
          onBlur={() => {
            if (notesDirty) saveNotes()
          }}
          rows={4}
          placeholder="Your thoughts…"
          className="w-full rounded-md border border-slate-300 bg-white p-3 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
        />
        {notesDirty && (
          <div className="mt-1 flex justify-end">
            <button
              onClick={saveNotes}
              className="text-xs text-slate-600 hover:underline"
            >
              Save notes
            </button>
          </div>
        )}
      </section>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Tags
        </h2>
        <input
          type="text"
          value={tagsText}
          onChange={(e) => {
            setTagsText(e.target.value)
            setTagsDirty(true)
          }}
          onBlur={() => {
            if (tagsDirty) saveTags()
          }}
          placeholder="fantasy, favourite"
          className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
        />
        <p className="mt-1 text-xs text-slate-500">Comma-separated.</p>
      </section>

      <section className="mt-10 border-t border-slate-200 pt-6">
        <button
          onClick={onDelete}
          className="rounded-md border border-red-300 bg-white px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-50"
        >
          Delete from library
        </button>
      </section>
    </div>
  )
}
