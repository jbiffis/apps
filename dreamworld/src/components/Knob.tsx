import type { ButtonHTMLAttributes, ReactNode } from 'react'

interface KnobProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  size?: number
  /** Rotation of the indicator pointer in degrees, -135 to +135 typical. */
  rotation?: number
  /** Visual tint when "on" (votes cast, etc.). */
  on?: boolean
  label?: ReactNode
  sublabel?: ReactNode
  showTicks?: boolean
}

/**
 * Amp-knob styled button. The pointer rotates to indicate state; the dial
 * face is a dark grippy disc with optional tick marks ringing it.
 */
export default function Knob({
  size = 56,
  rotation = -135,
  on = false,
  label,
  sublabel,
  showTicks = false,
  children,
  className = '',
  style,
  ...rest
}: KnobProps) {
  return (
    <div
      style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}
    >
      <div style={{ position: 'relative', width: size, height: size }}>
        {showTicks && (
          <svg
            className="knob-ticks"
            viewBox="-50 -50 100 100"
            width={size + 20}
            height={size + 20}
            style={{ position: 'absolute', top: -10, left: -10 }}
            aria-hidden
          >
            {Array.from({ length: 11 }).map((_, i) => {
              const angle = -135 + (i * 270) / 10
              const x1 = 44 * Math.sin((angle * Math.PI) / 180)
              const y1 = -44 * Math.cos((angle * Math.PI) / 180)
              const x2 = 49 * Math.sin((angle * Math.PI) / 180)
              const y2 = -49 * Math.cos((angle * Math.PI) / 180)
              return (
                <line
                  key={i}
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  stroke="rgba(244,229,200,0.55)"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                />
              )
            })}
          </svg>
        )}
        <button
          type="button"
          className={`knob ${on ? 'is-on' : ''} ${className}`}
          style={{ width: size, height: size, ...style }}
          {...rest}
        >
          <span
            className="knob-pointer"
            style={{
              transform: `translateX(-50%) rotate(${rotation}deg)`,
              transformOrigin: '50% calc(100% + 1px)',
            }}
          />
          {children && (
            <span
              style={{
                position: 'relative',
                fontFamily: 'JetBrains Mono, monospace',
                fontSize: Math.max(10, size * 0.22),
                fontWeight: 700,
                color: on ? 'var(--orange-bright)' : 'var(--cream)',
                textShadow: on
                  ? '0 0 8px var(--orange-glow)'
                  : '0 1px 0 rgba(0,0,0,0.5)',
                pointerEvents: 'none',
              }}
            >
              {children}
            </span>
          )}
        </button>
      </div>
      {label && (
        <span
          className="dial-label"
          style={{ marginTop: 2, color: on ? 'var(--orange-bright)' : 'rgba(244,229,200,0.65)' }}
        >
          {label}
        </span>
      )}
      {sublabel && (
        <span
          style={{
            fontFamily: 'JetBrains Mono, monospace',
            fontSize: 10,
            opacity: 0.5,
          }}
        >
          {sublabel}
        </span>
      )}
    </div>
  )
}
