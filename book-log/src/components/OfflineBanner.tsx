import { useOnline } from '../hooks/useOnline'

export default function OfflineBanner() {
  const online = useOnline()
  if (online) return null
  return (
    <div
      style={{
        background: 'var(--sticker-pink)',
        borderBottom: '2px solid var(--line)',
        padding: '8px 16px',
        textAlign: 'center',
        fontSize: 12,
        fontWeight: 800,
        color: 'var(--ink)',
      }}
    >
      🪁 You’re offline — need internet to add new books!
    </div>
  )
}
