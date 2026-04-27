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
        padding: '20px 16px 12px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        borderBottom: '1px solid rgba(58, 49, 44, 0.6)',
        background: 'linear-gradient(180deg, #1a1613 0%, #0e0c0a 100%)',
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
              color: 'rgba(244,229,200,0.55)',
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
