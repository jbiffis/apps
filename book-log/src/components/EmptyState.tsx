interface EmptyStateProps {
  message: string
  emoji?: string
}

export default function EmptyState({
  message,
  emoji = '📚',
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="mb-3 text-6xl" aria-hidden>
        {emoji}
      </div>
      <p className="max-w-xs text-base font-medium text-indigo-800">
        {message}
      </p>
    </div>
  )
}
