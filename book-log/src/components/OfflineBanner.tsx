import { useOnline } from '../hooks/useOnline'

export default function OfflineBanner() {
  const online = useOnline()
  if (online) return null
  return (
    <div className="bg-amber-200 px-4 py-2 text-center text-sm font-bold text-amber-900">
      🪁 You’re offline — need the internet to look up new books!
    </div>
  )
}
