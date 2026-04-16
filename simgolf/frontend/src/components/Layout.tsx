import { Link, useLocation } from 'react-router-dom'
import styles from './Layout.module.css'

interface LayoutProps {
  children: React.ReactNode
}

const NAV_ITEMS = [
  { to: '/',          label: 'Season' },
  { to: '/handicap',  label: 'Handicaps' },
  { to: '/prizes',    label: 'Prizes' },
  { to: '/admin',     label: 'Admin' },
]

export function Layout({ children }: LayoutProps) {
  const location = useLocation()

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.brand}>
            <span className={styles.brandIcon}>⛳</span>
            <span className={styles.brandText}>Creekside SIM Golf League</span>
            <span className={styles.brandIcon}>⛳</span>
          </Link>
        </div>
      </header>

      <nav className={styles.nav}>
        {NAV_ITEMS.map(item => (
          <Link
            key={item.to}
            to={item.to}
            className={`${styles.navLink} ${location.pathname === item.to ? styles.navLinkActive : ''}`}
          >
            {item.label}
          </Link>
        ))}
      </nav>

      <main className={styles.main}>
        {children}
      </main>

      <footer className={styles.footer}>
        <p>Creekside SIM Golf League &copy; 2026</p>
      </footer>
    </div>
  )
}
