import { useCallback, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import BarcodeScanner from '../components/BarcodeScanner'
import { addBook, getBook } from '../lib/db'
import { lookupBook, type BookLookup } from '../lib/openlibrary'
import type { Book } from '../lib/types'

type State =
  | { kind: 'scanning' }
  | { kind: 'looking-up'; isbn13: string }
  | { kind: 'found'; lookup: BookLookup }
  | { kind: 'duplicate'; book: Book }
  | { kind: 'not-found'; isbn13: string }
  | { kind: 'saving' }

export default function Scanner() {
  const navigate = useNavigate()
  const [state, setState] = useState<State>({ kind: 'scanning' })
  const lastHandledRef = useRef<string | null>(null)

  const handleDetected = useCallback(async (isbn13: string) => {
    if (lastHandledRef.current === isbn13) return
    lastHandledRef.current = isbn13

    const existing = await getBook(isbn13)
    if (existing) {
      setState({ kind: 'duplicate', book: existing })
      return
    }

    setState({ kind: 'looking-up', isbn13 })
    const lookup = await lookupBook(isbn13)
    if (!lookup) {
      setState({ kind: 'not-found', isbn13 })
      return
    }
    setState({ kind: 'found', lookup })
  }, [])

  async function saveFound() {
    if (state.kind !== 'found') return
    setState({ kind: 'saving' })
    const book: Book = {
      ...state.lookup,
      status: 'want-to-read',
      dateAdded: Date.now(),
      tags: [],
    }
    await addBook(book)
    navigate('/')
  }

  function resume() {
    lastHandledRef.current = null
    setState({ kind: 'scanning' })
  }

  const paused = state.kind !== 'scanning'

  return (
    <div className="mx-auto w-full max-w-md px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <Link to="/" className="text-sm text-slate-600 hover:underline">
          ← Back
        </Link>
        <Link to="/add" className="text-sm text-slate-600 hover:underline">
          Type ISBN instead
        </Link>
      </div>
      <h1 className="mb-4 text-2xl font-bold text-slate-900">Scan a book</h1>

      <BarcodeScanner onDetected={handleDetected} paused={paused} />

      <div className="mt-4">
        {state.kind === 'scanning' && (
          <p className="text-center text-sm text-slate-500">
            Point the camera at the barcode on the back of the book.
          </p>
        )}

        {state.kind === 'looking-up' && (
          <p className="text-center text-sm text-slate-600">
            Looking up {state.isbn13}…
          </p>
        )}

        {state.kind === 'found' && (
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex gap-4">
              {state.lookup.coverUrl && (
                <img
                  src={state.lookup.coverUrl}
                  alt=""
                  className="h-32 w-auto rounded bg-slate-100 object-cover"
                />
              )}
              <div className="flex flex-1 flex-col">
                <h2 className="font-semibold text-slate-900">
                  {state.lookup.title}
                </h2>
                <p className="text-sm text-slate-500">
                  {state.lookup.authors.join(', ')}
                </p>
                {state.lookup.publishDate && (
                  <p className="mt-1 text-xs text-slate-400">
                    {state.lookup.publishDate}
                  </p>
                )}
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <button
                onClick={resume}
                className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                Scan another
              </button>
              <button
                onClick={saveFound}
                className="flex-1 rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
              >
                Add to library
              </button>
            </div>
          </div>
        )}

        {state.kind === 'duplicate' && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
            <p className="text-sm text-amber-900">
              You already have <strong>{state.book.title}</strong> in your
              library.
            </p>
            <div className="mt-3 flex gap-2">
              <button
                onClick={resume}
                className="flex-1 rounded-md border border-amber-300 bg-white px-3 py-2 text-sm font-medium text-amber-900 hover:bg-amber-100"
              >
                Scan another
              </button>
              <button
                onClick={() => navigate(`/book/${state.book.id}`)}
                className="flex-1 rounded-md bg-amber-600 px-3 py-2 text-sm font-medium text-white hover:bg-amber-500"
              >
                View it
              </button>
            </div>
          </div>
        )}

        {state.kind === 'not-found' && (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4">
            <p className="text-sm text-red-900">
              No book found for ISBN {state.isbn13}.
            </p>
            <div className="mt-3 flex gap-2">
              <button
                onClick={resume}
                className="flex-1 rounded-md border border-red-300 bg-white px-3 py-2 text-sm font-medium text-red-900 hover:bg-red-100"
              >
                Scan another
              </button>
              <Link
                to="/add"
                className="flex-1 rounded-md bg-red-600 px-3 py-2 text-center text-sm font-medium text-white hover:bg-red-500"
              >
                Add manually
              </Link>
            </div>
          </div>
        )}

        {state.kind === 'saving' && (
          <p className="text-center text-sm text-slate-600">Saving…</p>
        )}
      </div>
    </div>
  )
}
