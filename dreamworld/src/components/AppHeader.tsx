interface AppHeaderProps {
  subtitle?: string
  rightSlot?: React.ReactNode
}

/**
 * Top "amp face" — Dreamworld plate plus a row of LEDs and a subtitle.
 */
export default function AppHeader({ subtitle, rightSlot }: AppHeaderProps) {
  return (
    <header
      style={{
        padding: '20px 16px 14px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        borderBottom: '3px solid var(--ink)',
        background:
          'linear-gradient(180deg, rgba(0,0,0,0.0) 0%, rgba(0,0,0,0.18) 100%)',
        boxShadow: '0 4px 14px rgba(0, 0, 0, 0.45)',
        position: 'relative',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span className="led" />
        <span className="brand-plate">Dreamworld</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        {subtitle && (
          <span
            className="dial-label"
            style={{
              fontSize: 10,
              letterSpacing: '0.22em',
              color: 'var(--ink)',
              opacity: 0.78,
              fontWeight: 700,
            }}
          >
            {subtitle}
          </span>
        )}
        {rightSlot}
      </div>
    </header>
  )
}
