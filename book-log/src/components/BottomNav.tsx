import { useLocation, useNavigate } from 'react-router-dom'
import Icon, { type IconName } from './Icon'

interface NavItem {
  id: string
  path: string
  label: string
  icon: IconName
  match?: (pathname: string) => boolean
}

const ITEMS: NavItem[] = [
  { id: 'home', path: '/', label: 'Home', icon: 'home' },
  {
    id: 'shelves',
    path: '/shelves',
    label: 'Shelves',
    icon: 'shelf',
    match: (p) => p.startsWith('/shelves') || p.startsWith('/book/'),
  },
  { id: 'quest', path: '/quest', label: 'Quest', icon: 'trophy' },
  { id: 'profile', path: '/profile', label: 'Me', icon: 'user' },
]

export default function BottomNav() {
  const location = useLocation()
  const navigate = useNavigate()

  const isActive = (item: NavItem) => {
    if (item.match) return item.match(location.pathname)
    if (item.path === '/') return location.pathname === '/'
    return location.pathname.startsWith(item.path)
  }

  return (
    <nav
      aria-label="Main"
      style={{
        position: 'fixed',
        left: '50%',
        transform: 'translateX(-50%)',
        bottom: 'calc(12px + env(safe-area-inset-bottom, 0px))',
        background: 'var(--paper)',
        border: '2px solid var(--line)',
        borderRadius: 999,
        boxShadow: 'var(--shadow)',
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        padding: 5,
        zIndex: 40,
        width: 'min(calc(100% - 24px), 360px)',
      }}
    >
      {ITEMS.slice(0, 2).map((item) => (
        <button
          key={item.id}
          type="button"
          onClick={() => navigate(item.path)}
          className={`nav-btn ${isActive(item) ? 'active' : ''}`}
        >
          <Icon name={item.icon} size={20} />
          {item.label}
        </button>
      ))}

      <button
        type="button"
        onClick={() => navigate('/add')}
        aria-label="Add book"
        style={{
          width: 52,
          height: 52,
          borderRadius: '50%',
          background: 'var(--accent-1)',
          border: '2px solid var(--line)',
          boxShadow: 'var(--shadow-sm)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          margin: '0 4px',
          flexShrink: 0,
          padding: 0,
        }}
      >
        <Icon name="plus" size={24} stroke="#fff" strokeWidth={3} />
      </button>

      {ITEMS.slice(2).map((item) => (
        <button
          key={item.id}
          type="button"
          onClick={() => navigate(item.path)}
          className={`nav-btn ${isActive(item) ? 'active' : ''}`}
        >
          <Icon name={item.icon} size={20} />
          {item.label}
        </button>
      ))}
    </nav>
  )
}
