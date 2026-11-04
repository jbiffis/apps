import { Link, useLocation } from 'react-router-dom'
import styles from './Layout.module.css'

interface LayoutProps {
  children: React.ReactNode
}

const NAV_ITEMS = [
  { to: '/',          label: 'Standings' },
  { to: '/handicap',  label: 'Handicaps' },
  { to: '/prizes',    label: 'Prizes' },
  { to: '/admin',     label: 'Admin' },
]

export function Layout({ children }: LayoutProps) {
  const location = useLocation()

  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#main-content">Skip to content</a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.brand}>
            <span className={styles.brandIcon} aria-hidden="true">
              <svg viewBox="0 0 32 36" fill="none"><path d="M12 28V5l14 6-14 6" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /><ellipse cx="15" cy="29" rx="10" ry="3" stroke="currentColor" strokeWidth="1.5" /><path d="m23 5 2-2M27 19l3 1" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" /></svg>
            </span>
            <span className={styles.brandText}>Creekside<span><small>Sim Golf League</small></span></span>
          </Link>
          <p className={styles.clubNote}><span aria-hidden="true" />Have fun! No Politics, Racial, or Gay conversations</p>
        </div>
      </header>

      <nav className={styles.nav}>
        <div className={styles.navInner}>
        {NAV_ITEMS.map(item => (
          <Link
            key={item.to}
            to={item.to}
            aria-current={(item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to)) ? 'page' : undefined}
            className={`${styles.navLink} ${(item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to)) ? styles.navLinkActive : ''} ${item.to === '/admin' ? styles.adminLink : ''}`}
          >
            {item.label}
          </Link>
        ))}
        </div>
      </nav>

      <main id="main-content" className={styles.main} tabIndex={-1}>
        {children}
      </main>

      <footer className={styles.footer}>
        <p>Creekside Sim Golf League &copy; {new Date().getFullYear()}</p>
        <p>Good company. Better golf.</p>
      </footer>
    </div>
  )
}
