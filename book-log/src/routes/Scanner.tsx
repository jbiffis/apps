import { Link } from 'react-router-dom'

export default function Scanner() {
  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-6">
      <h1 className="text-2xl font-bold text-slate-900">Scan a book</h1>
      <p className="mt-2 text-sm text-slate-500">Scanner — implemented in Phase 3.</p>
      <Link to="/" className="mt-4 inline-block text-sm text-slate-700 underline">
        Back to library
      </Link>
    </div>
  )
}
