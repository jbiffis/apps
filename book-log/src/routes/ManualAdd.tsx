import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { addBook, getBook } from '../lib/db'
import { isValidIsbn, normalizeIsbn, toIsbn13 } from '../lib/isbn'
import { lookupBook } from '../lib/openlibrary'
import type { Book } from '../lib/types'

type State =
  | { kind: 'idle' }
  | { kind: 'looking-up' }
  | { kind: 'error'; message: string }
  | { kind: 'saving' }

export default function ManualAdd() {
  const navigate = useNavigate()
  const [isbn, setIsbn] = useState('')
  const [state, setState] = useState<State>({ kind: 'idle' })

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    const raw = normalizeIsbn(isbn)
    if (!isValidIsbn(raw)) {
      setState({
        kind: 'error',
        message: 'That number doesn’t look right — check it again?',
      })
      return
    }
    const isbn13 = toIsbn13(raw)
    if (!isbn13) {
      setState({ kind: 'error', message: 'Could not read that number.' })
      return
    }

    const existing = await getBook(isbn13)
    if (existing) {
      navigate(`/book/${isbn13}`)
      return
    }

    setState({ kind: 'looking-up' })
    const lookup = await lookupBook(isbn13)
    if (!lookup) {
      setState({
        kind: 'error',
        message: 'We couldn’t find that book. Double-check the number!',
      })
      return
    }

    setState({ kind: 'saving' })
    const book: Book = {
      ...lookup,
      status: 'want-to-read',
      dateAdded: Date.now(),
      tags: [],
    }
    await addBook(book)
    navigate('/')
  }

  const busy = state.kind === 'looking-up' || state.kind === 'saving'

  return (
    <div className="mx-auto w-full max-w-md px-4 py-6">
      <div className="mb-4">
        <Link
          to="/"
          className="inline-flex items-center gap-1 rounded-full border-2 border-brand-200 bg-white px-3 py-1.5 text-sm font-bold text-brand-700 hover:bg-brand-50"
        >
          ← Bookshelf
        </Link>
      </div>
      <h1 className="text-3xl font-bold text-indigo-950">
        Type the book number <span aria-hidden>✏️</span>
      </h1>
      <p className="mt-2 text-base font-medium text-indigo-700">
        On the back of the book, near the barcode, you’ll see a number that
        starts with <span className="font-mono font-bold">978</span>. Type it in
        here!
      </p>
      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <div>
          <label
            htmlFor="isbn"
            className="block text-sm font-bold text-indigo-600"
          >
            Book number (ISBN)
          </label>
          <input
            id="isbn"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            autoFocus
            value={isbn}
            onChange={(e) => {
              setIsbn(e.target.value)
              if (state.kind === 'error') setState({ kind: 'idle' })
            }}
            placeholder="9780441172719"
            className="mt-1 w-full rounded-2xl border-4 border-brand-200 bg-white px-4 py-3 text-lg font-semibold text-indigo-900 shadow-chunkySm focus:border-brand-500 focus:outline-none"
          />
        </div>
        {state.kind === 'error' && (
          <p className="rounded-xl bg-rose-100 px-3 py-2 text-sm font-semibold text-rose-800">
            {state.message}
          </p>
        )}
        <button
          type="submit"
          disabled={busy || isbn.trim() === ''}
          className="inline-flex w-full items-center justify-center gap-2 rounded-full bg-brand-600 px-4 py-3 text-base font-bold text-white shadow-chunky hover:-translate-y-0.5 hover:bg-brand-500 disabled:cursor-not-allowed disabled:bg-indigo-300 disabled:shadow-none"
        >
          {state.kind === 'looking-up'
            ? '🔎 Looking…'
            : state.kind === 'saving'
              ? '✨ Saving…'
              : '🔎 Find my book'}
        </button>
      </form>
    </div>
  )
}
