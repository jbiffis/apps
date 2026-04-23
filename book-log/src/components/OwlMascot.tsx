interface OwlMascotProps {
  size?: number
  mood?: 'happy' | 'blink' | 'reading'
  className?: string
}

export default function OwlMascot({
  size = 120,
  mood = 'happy',
  className = '',
}: OwlMascotProps) {
  const blink = mood === 'blink'
  return (
    <div
      className={className}
      style={{ width: size, height: size * 1.2, position: 'relative' }}
      aria-hidden
    >
      <svg viewBox="0 0 140 168" width="100%" height="100%" style={{ overflow: 'visible' }}>
        <ellipse cx="70" cy="160" rx="28" ry="4" fill="rgba(0,0,0,0.15)" />
        <path
          d="M 70 10 Q 100 10 116 28 Q 125 38 123 60 Q 128 85 122 110 Q 115 140 88 148 Q 70 152 52 148 Q 25 140 18 110 Q 12 85 17 60 Q 20 38 32 25 Q 50 10 70 10 Z"
          fill="var(--accent-2)"
          stroke="var(--line)"
          strokeWidth="3"
          strokeLinejoin="round"
        />
        <path
          d="M 70 58 Q 95 60 98 85 Q 100 115 88 135 Q 70 142 52 135 Q 40 115 42 85 Q 45 60 70 58 Z"
          fill="var(--paper)"
          stroke="var(--line)"
          strokeWidth="2"
          opacity="0.95"
        />
        <path
          d="M 60 85 q 4 -3 8 0 M 72 85 q 4 -3 8 0 M 64 95 q 4 -3 8 0 M 76 95 q 4 -3 8 0 M 60 105 q 4 -3 8 0 M 72 105 q 4 -3 8 0"
          stroke="var(--ink-soft)"
          strokeWidth="1.2"
          fill="none"
          strokeLinecap="round"
          opacity="0.35"
        />
        <g>
          <path
            d="M 28 55 Q 20 75 24 100 Q 30 120 48 125 Q 50 115 48 100 Q 50 80 48 62 Q 40 50 28 55 Z"
            fill="var(--accent-1)"
            stroke="var(--line)"
            strokeWidth="2.5"
            strokeLinejoin="round"
          />
          <path
            d="M 30 70 q 5 5 10 0 M 30 85 q 5 5 10 0 M 30 100 q 5 5 10 0 M 30 115 q 5 5 10 0"
            stroke="var(--line)"
            strokeWidth="1.5"
            fill="none"
            strokeLinecap="round"
          />
        </g>
        <g>
          <path
            d="M 112 55 Q 120 75 116 100 Q 110 120 92 125 Q 90 115 92 100 Q 90 80 92 62 Q 100 50 112 55 Z"
            fill="var(--accent-1)"
            stroke="var(--line)"
            strokeWidth="2.5"
            strokeLinejoin="round"
          />
          <path
            d="M 100 70 q 5 5 10 0 M 100 85 q 5 5 10 0 M 100 100 q 5 5 10 0 M 100 115 q 5 5 10 0"
            stroke="var(--line)"
            strokeWidth="1.5"
            fill="none"
            strokeLinecap="round"
          />
        </g>
        <circle cx="52" cy="44" r="14" fill="var(--paper)" stroke="var(--line)" strokeWidth="3" />
        {blink ? (
          <path d="M 46 44 q 6 -4 12 0" stroke="var(--line)" strokeWidth="3" fill="none" strokeLinecap="round" />
        ) : (
          <>
            <circle cx="52" cy="44" r="6" fill="var(--ink)" />
            <circle cx="54" cy="42" r="2" fill="#fff" />
          </>
        )}
        <circle cx="88" cy="44" r="14" fill="var(--paper)" stroke="var(--line)" strokeWidth="3" />
        {blink ? (
          <path d="M 82 44 q 6 -4 12 0" stroke="var(--line)" strokeWidth="3" fill="none" strokeLinecap="round" />
        ) : (
          <>
            <circle cx="88" cy="44" r="6" fill="var(--ink)" />
            <circle cx="90" cy="42" r="2" fill="#fff" />
          </>
        )}
        <path d="M 66 44 L 74 44" stroke="var(--line)" strokeWidth="2.5" strokeLinecap="round" />
        <ellipse cx="40" cy="60" rx="6" ry="3.5" fill="var(--accent-1)" opacity="0.35" />
        <ellipse cx="100" cy="60" rx="6" ry="3.5" fill="var(--accent-1)" opacity="0.35" />
        <path
          d="M 65 60 Q 70 58 75 60 L 70 70 Z"
          fill="var(--accent-1)"
          stroke="var(--line)"
          strokeWidth="2"
          strokeLinejoin="round"
        />
        <path
          d="M 55 148 Q 50 155 48 160 Q 54 162 58 160 Q 56 155 58 152 Q 60 155 62 160 Q 66 162 68 160 Q 64 155 62 148 Z"
          fill="var(--accent-1)"
          stroke="var(--line)"
          strokeWidth="2"
          strokeLinejoin="round"
        />
        <path
          d="M 75 148 Q 72 155 74 160 Q 78 162 82 160 Q 80 155 82 152 Q 84 155 86 160 Q 90 162 92 160 Q 88 155 85 148 Z"
          fill="var(--accent-1)"
          stroke="var(--line)"
          strokeWidth="2"
          strokeLinejoin="round"
        />
        {mood === 'reading' && (
          <g transform="translate(70, 130)">
            <rect
              x="-26"
              y="-8"
              width="52"
              height="20"
              rx="2"
              fill="var(--accent-3)"
              stroke="var(--line)"
              strokeWidth="2.5"
            />
            <line x1="0" y1="-8" x2="0" y2="12" stroke="var(--line)" strokeWidth="2" />
            <line x1="-20" y1="-2" x2="-6" y2="-2" stroke="rgba(255,255,255,0.5)" strokeWidth="1.2" />
            <line x1="-20" y1="3" x2="-6" y2="3" stroke="rgba(255,255,255,0.5)" strokeWidth="1.2" />
            <line x1="6" y1="-2" x2="20" y2="-2" stroke="rgba(255,255,255,0.5)" strokeWidth="1.2" />
            <line x1="6" y1="3" x2="20" y2="3" stroke="rgba(255,255,255,0.5)" strokeWidth="1.2" />
          </g>
        )}
      </svg>
    </div>
  )
}
