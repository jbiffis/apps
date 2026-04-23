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
      setState({ kind: 'error', message: 'That ISBN doesn’t look valid.' })
      return
    }
    const isbn13 = toIsbn13(raw)
    if (!isbn13) {
      setState({ kind: 'error', message: 'Could not parse ISBN.' })
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
        message: 'No book found for that ISBN. Try another?',
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
        <Link to="/" className="text-sm text-slate-600 hover:underline">
          ← Back to library
        </Link>
      </div>
      <h1 className="text-2xl font-bold text-slate-900">Add by ISBN</h1>
      <p className="mt-1 text-sm text-slate-500">
        Paste or type an ISBN-10 or ISBN-13.
      </p>
      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <div>
          <label htmlFor="isbn" className="block text-sm font-medium text-slate-700">
            ISBN
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
            className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-base shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
          />
        </div>
        {state.kind === 'error' && (
          <p className="text-sm text-red-600">{state.message}</p>
        )}
        <button
          type="submit"
          disabled={busy || isbn.trim() === ''}
          className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
        >
          {state.kind === 'looking-up'
            ? 'Looking up…'
            : state.kind === 'saving'
              ? 'Saving…'
              : 'Look up and save'}
        </button>
      </form>
    </div>
  )
}
