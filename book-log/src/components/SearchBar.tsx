import type { ReadingStatus } from '../lib/types'
import { STATUS_META } from '../lib/status'

export type StatusFilter = ReadingStatus | 'all'

interface SearchBarProps {
  query: string
  onQueryChange: (query: string) => void
  filter: StatusFilter
  onFilterChange: (filter: StatusFilter) => void
}

const FILTERS: { value: StatusFilter; label: string; emoji: string }[] = [
  { value: 'all', label: 'All', emoji: '✨' },
  {
    value: 'reading',
    label: STATUS_META.reading.label,
    emoji: STATUS_META.reading.emoji,
  },
  {
    value: 'read',
    label: STATUS_META.read.label,
    emoji: STATUS_META.read.emoji,
  },
  {
    value: 'want-to-read',
    label: STATUS_META['want-to-read'].label,
    emoji: STATUS_META['want-to-read'].emoji,
  },
]

export default function SearchBar({
  query,
  onQueryChange,
  filter,
  onFilterChange,
}: SearchBarProps) {
  return (
    <div className="mb-5 flex flex-col gap-3">
      <div className="relative">
        <span
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg"
          aria-hidden
        >
          🔍
        </span>
        <input
          type="search"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="Find a book…"
          className="w-full rounded-2xl border-4 border-brand-200 bg-white py-3 pl-10 pr-3 text-base font-medium text-indigo-900 shadow-chunkySm placeholder:text-indigo-400 focus:border-brand-500 focus:outline-none"
        />
      </div>
      <div className="flex gap-2 overflow-x-auto pb-1">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => onFilterChange(f.value)}
            className={
              filter === f.value
                ? 'flex shrink-0 items-center gap-1 rounded-full bg-brand-600 px-4 py-2 text-sm font-bold text-white shadow-chunkySm'
                : 'flex shrink-0 items-center gap-1 rounded-full border-2 border-brand-200 bg-white px-4 py-2 text-sm font-semibold text-brand-700 hover:bg-brand-50'
            }
          >
            <span aria-hidden>{f.emoji}</span>
            {f.label}
          </button>
        ))}
      </div>
    </div>
  )
}
