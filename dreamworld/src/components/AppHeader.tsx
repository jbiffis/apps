interface AppHeaderProps {
  subtitle?: string
  rightSlot?: React.ReactNode
}

export default function AppHeader({ subtitle, rightSlot }: AppHeaderProps) {
  return (
    <header>
      {/* Chrome nameplate zone */}
      <div
        className="chrome-face"
        style={{
          padding: '10px 14px 10px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span className="led" />
          <span className="brand-plate">Dreamworld</span>
        </div>
        {rightSlot && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {rightSlot}
          </div>
        )}
      </div>

      {/* Orange control strip */}
      {subtitle && (
        <div
          className="orange-strip"
          style={{
            padding: '5px 14px',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          <span
            style={{
              fontFamily: 'Inter, sans-serif',
              fontWeight: 800,
              fontSize: 10,
              letterSpacing: '0.24em',
              textTransform: 'uppercase',
              color: 'rgba(20,8,2,0.75)',
            }}
          >
            {subtitle}
          </span>
          {/* Decorative tick marks */}
          <div style={{ flex: 1, display: 'flex', gap: 4, alignItems: 'center', justifyContent: 'flex-end' }}>
            {[0, 1, 2, 3, 4, 5, 6, 7].map((i) => (
              <div
                key={i}
                style={{
                  width: i % 4 === 0 ? 3 : 2,
                  height: i % 4 === 0 ? 12 : 8,
                  background: 'rgba(0,0,0,0.4)',
                  borderRadius: 1,
                }}
              />
            ))}
          </div>
        </div>
      )}
    </header>
  )
}
