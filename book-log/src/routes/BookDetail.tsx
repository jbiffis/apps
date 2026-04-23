import { Link, useParams } from 'react-router-dom'

export default function BookDetail() {
  const { id } = useParams()

  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-6">
      <h1 className="text-2xl font-bold text-slate-900">Book {id}</h1>
      <p className="mt-2 text-sm text-slate-500">Detail view — implemented in Phase 4.</p>
      <Link to="/" className="mt-4 inline-block text-sm text-slate-700 underline">
        Back to library
      </Link>
    </div>
  )
}
