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
        <Link
          to="/"
          className="inline-flex items-center gap-1 rounded-full border-2 border-brand-200 bg-white px-3 py-1.5 text-sm font-bold text-brand-700 hover:bg-brand-50"
        >
          ← Back
        </Link>
        <Link
          to="/add"
          className="text-sm font-semibold text-brand-700 underline-offset-2 hover:underline"
        >
          Type the number →
        </Link>
      </div>
      <h1 className="mb-2 text-3xl font-bold text-indigo-950">
        Scan a book! <span aria-hidden>📷</span>
      </h1>
      <p className="mb-4 text-base font-medium text-indigo-700">
        Find the barcode on the back cover and hold it steady.
      </p>

      <div className="overflow-hidden rounded-3xl border-4 border-brand-300 shadow-chunky">
        <BarcodeScanner onDetected={handleDetected} paused={paused} />
      </div>

      <div className="mt-4">
        {state.kind === 'scanning' && (
          <p className="text-center text-sm font-medium text-indigo-700">
            Line up the barcode inside the box…
          </p>
        )}

        {state.kind === 'looking-up' && (
          <div className="rounded-2xl bg-white p-4 text-center shadow-chunkySm">
            <div className="animate-pulse text-3xl">🔎</div>
            <p className="mt-1 text-base font-semibold text-indigo-800">
              Looking up your book…
            </p>
          </div>
        )}

        {state.kind === 'found' && (
          <div className="animate-pop-in rounded-3xl border-4 border-emerald-300 bg-white p-4 shadow-chunky">
            <div className="flex gap-4">
              {state.lookup.coverUrl ? (
                <img
                  src={state.lookup.coverUrl}
                  alt=""
                  className="h-32 w-auto rounded-xl bg-amber-100 object-cover"
                />
              ) : (
                <div className="flex h-32 w-20 items-center justify-center rounded-xl bg-amber-100 text-4xl">
                  📖
                </div>
              )}
              <div className="flex flex-1 flex-col">
                <p className="text-xs font-bold uppercase tracking-wide text-emerald-600">
                  Found it!
                </p>
                <h2 className="mt-1 text-lg font-bold leading-tight text-indigo-950">
                  {state.lookup.title}
                </h2>
                <p className="text-sm font-medium text-indigo-700">
                  {state.lookup.authors.join(', ')}
                </p>
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <button
                onClick={resume}
                className="flex-1 rounded-full border-2 border-brand-200 bg-white px-3 py-2 text-sm font-bold text-brand-700 hover:bg-brand-50"
              >
                Scan another
              </button>
              <button
                onClick={saveFound}
                className="flex-1 rounded-full bg-emerald-500 px-3 py-2 text-sm font-bold text-white shadow-chunkySm hover:bg-emerald-400"
              >
                ✨ Add to my shelf
              </button>
            </div>
          </div>
        )}

        {state.kind === 'duplicate' && (
          <div className="animate-pop-in rounded-3xl border-4 border-amber-300 bg-amber-50 p-4">
            <p className="text-base font-semibold text-amber-900">
              You already have <strong>{state.book.title}</strong>! 📚
            </p>
            <div className="mt-3 flex gap-2">
              <button
                onClick={resume}
                className="flex-1 rounded-full border-2 border-amber-300 bg-white px-3 py-2 text-sm font-bold text-amber-900 hover:bg-amber-100"
              >
                Scan another
              </button>
              <button
                onClick={() => navigate(`/book/${state.book.id}`)}
                className="flex-1 rounded-full bg-amber-500 px-3 py-2 text-sm font-bold text-white shadow-chunkySm hover:bg-amber-400"
              >
                Go to it →
              </button>
            </div>
          </div>
        )}

        {state.kind === 'not-found' && (
          <div className="animate-pop-in rounded-3xl border-4 border-rose-200 bg-rose-50 p-4">
            <p className="text-base font-semibold text-rose-900">
              Hmm, we couldn’t find that book 🤔
            </p>
            <p className="mt-1 text-sm text-rose-800">
              ISBN: <span className="font-mono">{state.isbn13}</span>
            </p>
            <div className="mt-3 flex gap-2">
              <button
                onClick={resume}
                className="flex-1 rounded-full border-2 border-rose-300 bg-white px-3 py-2 text-sm font-bold text-rose-900 hover:bg-rose-100"
              >
                Try again
              </button>
              <Link
                to="/add"
                className="flex-1 rounded-full bg-rose-500 px-3 py-2 text-center text-sm font-bold text-white shadow-chunkySm hover:bg-rose-400"
              >
                Type it in
              </Link>
            </div>
          </div>
        )}

        {state.kind === 'saving' && (
          <p className="text-center text-sm font-semibold text-indigo-700">
            Adding to your shelf…
          </p>
        )}
      </div>
    </div>
  )
}
