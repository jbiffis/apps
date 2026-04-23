import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteBook, updateBook } from '../lib/db'
import { useBook } from '../hooks/useBooks'
import { STATUS_META, STATUS_ORDER } from '../lib/status'
import type { Book, ReadingStatus } from '../lib/types'

export default function BookDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const book = useBook(id)

  const [notes, setNotes] = useState('')
  const [tagsText, setTagsText] = useState('')
  const [notesDirty, setNotesDirty] = useState(false)
  const [tagsDirty, setTagsDirty] = useState(false)
  const [celebrate, setCelebrate] = useState(false)

  useEffect(() => {
    if (book) {
      if (!notesDirty) setNotes(book.notes ?? '')
      if (!tagsDirty) setTagsText(book.tags.join(', '))
    }
  }, [book, notesDirty, tagsDirty])

  if (book === undefined) {
    return (
      <div className="mx-auto w-full max-w-3xl px-4 py-6 text-center text-base font-medium text-indigo-700">
        Loading…
      </div>
    )
  }

  if (book === null) {
    return (
      <div className="mx-auto w-full max-w-3xl px-4 py-6 text-center">
        <div className="text-5xl">🤷</div>
        <p className="mt-3 text-lg font-semibold text-indigo-900">
          We couldn’t find that book.
        </p>
        <Link
          to="/"
          className="mt-4 inline-flex items-center gap-1 rounded-full bg-brand-600 px-4 py-2 text-sm font-bold text-white shadow-chunkySm hover:bg-brand-500"
        >
          ← Back to bookshelf
        </Link>
      </div>
    )
  }

  async function setStatus(status: ReadingStatus) {
    if (!book) return
    const changes: Partial<Book> = { status }
    const becameRead = status === 'read' && book.status !== 'read'
    if (becameRead && !book.dateFinished) {
      changes.dateFinished = Date.now()
    }
    await updateBook(book.id, changes)
    if (becameRead) {
      setCelebrate(true)
      setTimeout(() => setCelebrate(false), 1800)
    }
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
    if (!confirm(`Remove "${book.title}" from your bookshelf?`)) return
    await deleteBook(book.id)
    navigate('/')
  }

  const currentMeta = STATUS_META[book.status]

  return (
    <div className="mx-auto w-full max-w-3xl px-4 pb-12 pt-6">
      {celebrate && (
        <div className="pointer-events-none fixed inset-0 z-50 flex items-center justify-center">
          <div className="animate-pop-in rounded-3xl bg-white px-8 py-6 text-center shadow-chunky">
            <div className="text-6xl">🎉</div>
            <p className="mt-2 text-2xl font-bold text-emerald-600">
              You finished a book!
            </p>
          </div>
        </div>
      )}

      <div className="mb-4">
        <Link
          to="/"
          className="inline-flex items-center gap-1 rounded-full border-2 border-brand-200 bg-white px-3 py-1.5 text-sm font-bold text-brand-700 hover:bg-brand-50"
        >
          ← Bookshelf
        </Link>
      </div>

      <div
        className={`flex flex-col gap-6 rounded-3xl border-4 bg-white p-5 shadow-chunky sm:flex-row ${currentMeta.cardBorder}`}
      >
        <div className="w-full sm:w-48">
          {book.coverUrl ? (
            <img
              src={book.coverUrl}
              alt={`Cover of ${book.title}`}
              className="w-full rounded-2xl bg-amber-100 object-cover shadow"
            />
          ) : (
            <div className="flex aspect-[2/3] w-full items-center justify-center rounded-2xl bg-amber-100 text-5xl">
              📖
            </div>
          )}
        </div>

        <div className="flex-1">
          <span
            className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${currentMeta.cardBadge}`}
          >
            <span aria-hidden>{currentMeta.emoji}</span>
            {currentMeta.label}
          </span>
          <h1 className="mt-2 text-2xl font-bold leading-tight text-indigo-950">
            {book.title}
          </h1>
          <p className="mt-1 text-base font-medium text-indigo-700">
            by {book.authors.join(', ') || 'unknown'}
          </p>
          <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            {book.pageCount && (
              <>
                <dt className="text-indigo-500">Pages</dt>
                <dd className="font-semibold text-indigo-900">
                  {book.pageCount}
                </dd>
              </>
            )}
            {book.publishDate && (
              <>
                <dt className="text-indigo-500">Published</dt>
                <dd className="font-semibold text-indigo-900">
                  {book.publishDate}
                </dd>
              </>
            )}
            {book.publisher && (
              <>
                <dt className="text-indigo-500">Publisher</dt>
                <dd className="font-semibold text-indigo-900">
                  {book.publisher}
                </dd>
              </>
            )}
          </dl>
        </div>
      </div>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-bold uppercase tracking-wide text-indigo-600">
          What’s the deal?
        </h2>
        <div className="flex flex-wrap gap-2">
          {STATUS_ORDER.map((s) => {
            const meta = STATUS_META[s]
            const active = book.status === s
            return (
              <button
                key={s}
                onClick={() => setStatus(s)}
                className={`inline-flex items-center gap-1 rounded-full border-2 px-4 py-2 text-sm font-bold transition ${
                  active ? meta.chipActive : meta.chip
                }`}
              >
                <span aria-hidden>{meta.emoji}</span>
                {meta.label}
              </button>
            )
          })}
        </div>
      </section>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-bold uppercase tracking-wide text-indigo-600">
          How good was it?
        </h2>
        <div className="flex items-center gap-1">
          {[1, 2, 3, 4, 5].map((n) => {
            const active = (book.rating ?? 0) >= n
            return (
              <button
                key={n}
                onClick={() =>
                  setRating(
                    book.rating === n ? undefined : (n as 1 | 2 | 3 | 4 | 5),
                  )
                }
                aria-label={`${n} star${n > 1 ? 's' : ''}`}
                className={`text-4xl transition ${
                  active
                    ? 'text-amber-400 drop-shadow hover:scale-110'
                    : 'text-indigo-200 hover:text-amber-300'
                }`}
              >
                ★
              </button>
            )
          })}
          {book.rating && (
            <button
              onClick={() => setRating(undefined)}
              className="ml-2 text-xs font-semibold text-indigo-500 hover:underline"
            >
              clear
            </button>
          )}
        </div>
      </section>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-bold uppercase tracking-wide text-indigo-600">
          My thoughts
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
          placeholder="Best part? Favorite character? Write it down!"
          className="w-full rounded-2xl border-4 border-brand-200 bg-white p-3 text-sm font-medium text-indigo-900 shadow-chunkySm placeholder:text-indigo-400 focus:border-brand-500 focus:outline-none"
        />
      </section>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-bold uppercase tracking-wide text-indigo-600">
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
          placeholder="funny, magic, mystery…"
          className="w-full rounded-2xl border-4 border-brand-200 bg-white px-3 py-2 text-sm font-medium text-indigo-900 shadow-chunkySm placeholder:text-indigo-400 focus:border-brand-500 focus:outline-none"
        />
        <p className="mt-1 text-xs font-medium text-indigo-500">
          Separate with commas.
        </p>
      </section>

      <section className="mt-10">
        <button
          onClick={onDelete}
          className="rounded-full border-2 border-rose-300 bg-white px-4 py-2 text-sm font-bold text-rose-700 hover:bg-rose-50"
        >
          🗑️ Remove from shelf
        </button>
      </section>
    </div>
  )
}
