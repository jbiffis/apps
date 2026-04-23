import { Link } from 'react-router-dom'

export default function ManualAdd() {
  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-6">
      <h1 className="text-2xl font-bold text-slate-900">Add by ISBN</h1>
      <p className="mt-2 text-sm text-slate-500">
        Manual ISBN entry — implemented in Phase 2.
      </p>
      <Link to="/" className="mt-4 inline-block text-sm text-slate-700 underline">
        Back to library
      </Link>
    </div>
  )
}
