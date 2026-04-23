import type { ReadingStatus } from '../lib/types'

export type StatusFilter = ReadingStatus | 'all'

interface SearchBarProps {
  query: string
  onQueryChange: (query: string) => void
  filter: StatusFilter
  onFilterChange: (filter: StatusFilter) => void
}

const FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'reading', label: 'Reading' },
  { value: 'read', label: 'Read' },
  { value: 'want-to-read', label: 'Want to read' },
]

export default function SearchBar({
  query,
  onQueryChange,
  filter,
  onFilterChange,
}: SearchBarProps) {
  return (
    <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
      <input
        type="search"
        value={query}
        onChange={(e) => onQueryChange(e.target.value)}
        placeholder="Search title, author, tag…"
        className="flex-1 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
      />
      <div className="flex gap-1 overflow-x-auto">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => onFilterChange(f.value)}
            className={
              filter === f.value
                ? 'whitespace-nowrap rounded-full bg-slate-900 px-3 py-1 text-xs font-medium text-white'
                : 'whitespace-nowrap rounded-full border border-slate-300 bg-white px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50'
            }
          >
            {f.label}
          </button>
        ))}
      </div>
    </div>
  )
}
