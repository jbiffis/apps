interface BookStoryLogoProps {
  size?: number
  className?: string
}

/**
 * Wonky hand-lettered BookStory logo — "Book" on top, open book in the
 * middle, "Story" on the bottom. Ported from the design bundle.
 */
export default function BookStoryLogo({
  size = 240,
  className,
}: BookStoryLogoProps) {
  const c1 = 'var(--accent-1)'
  const c2 = 'var(--accent-2)'
  const lineCol = 'var(--line)'
  const paperCol = 'var(--paper)'
  const acc3 = 'var(--accent-3)'
  return (
    <svg
      viewBox="0 0 400 420"
      width={size}
      height={(size * 420) / 400}
      className={className}
      style={{ overflow: 'visible' }}
      aria-label="BookStory"
    >
      {/* BOOK */}
      <g
        fontFamily="Fraunces, Georgia, serif"
        fontWeight="800"
        fontSize="92"
        textAnchor="middle"
      >
        <g transform="translate(90, 90) rotate(-4)">
          <text x="0" y="0" fill={c1} stroke={lineCol} strokeWidth="5" paintOrder="stroke">
            B
          </text>
        </g>
        <g transform="translate(160, 85) rotate(2)">
          <text x="0" y="0" fill={c2} stroke={lineCol} strokeWidth="5" paintOrder="stroke">
            o
          </text>
        </g>
        <g transform="translate(225, 92) rotate(-2)">
          <text x="0" y="0" fill={c2} stroke={lineCol} strokeWidth="5" paintOrder="stroke">
            o
          </text>
        </g>
        <g transform="translate(295, 88) rotate(5)">
          <text x="0" y="0" fill={c1} stroke={lineCol} strokeWidth="5" paintOrder="stroke">
            k
          </text>
        </g>
      </g>

      {/* Open book in the middle */}
      <g transform="translate(200, 175)">
        <path
          d="M -75 -5 L 75 -5 L 75 55 L -75 55 Z"
          fill="rgba(0,0,0,0.08)"
          transform="translate(3,3)"
        />
        <path
          d="M -75 -5 Q -78 -8 -75 -10 L 0 -2 L 0 52 L -75 48 Q -78 45 -75 42 Z"
          fill={paperCol}
          stroke={lineCol}
          strokeWidth="3.5"
          strokeLinejoin="round"
        />
        <path
          d="M 75 -5 Q 78 -8 75 -10 L 0 -2 L 0 52 L 75 48 Q 78 45 75 42 Z"
          fill={paperCol}
          stroke={lineCol}
          strokeWidth="3.5"
          strokeLinejoin="round"
        />
        <line x1="0" y1="-2" x2="0" y2="52" stroke={lineCol} strokeWidth="3" />
        <path
          d="M -65 10 q 10 -4 20 0 t 20 0 t 20 0"
          stroke={c1}
          strokeWidth="3"
          fill="none"
          strokeLinecap="round"
        />
        <path
          d="M -65 22 q 10 -4 20 0 t 20 0 t 20 0"
          stroke={c1}
          strokeWidth="3"
          fill="none"
          strokeLinecap="round"
        />
        <path
          d="M -65 34 q 10 -4 20 0 t 20 0 t 20 0"
          stroke={c1}
          strokeWidth="3"
          fill="none"
          strokeLinecap="round"
        />
        <path
          d="M 5 10 q 10 -4 20 0 t 20 0 t 20 0"
          stroke={acc3}
          strokeWidth="3"
          fill="none"
          strokeLinecap="round"
        />
        <path
          d="M 5 22 q 10 -4 20 0 t 20 0 t 20 0"
          stroke={acc3}
          strokeWidth="3"
          fill="none"
          strokeLinecap="round"
        />
        <path
          d="M 5 34 q 10 -4 20 0 t 20 0 t 20 0"
          stroke={acc3}
          strokeWidth="3"
          fill="none"
          strokeLinecap="round"
        />
      </g>

      {/* STORY */}
      <g
        fontFamily="Fraunces, Georgia, serif"
        fontWeight="800"
        fontSize="92"
        textAnchor="middle"
      >
        <g transform="translate(80, 320) rotate(-6)">
          <text x="0" y="0" fill={c2} stroke={lineCol} strokeWidth="5" paintOrder="stroke">
            S
          </text>
        </g>
        <g transform="translate(150, 330) rotate(3)">
          <text x="0" y="0" fill={c1} stroke={lineCol} strokeWidth="5" paintOrder="stroke">
            t
          </text>
        </g>
        <g transform="translate(210, 325) rotate(-3)">
          <text x="0" y="0" fill={c2} stroke={lineCol} strokeWidth="5" paintOrder="stroke">
            o
          </text>
        </g>
        <g transform="translate(280, 335) rotate(5)">
          <text x="0" y="0" fill={acc3} stroke={lineCol} strokeWidth="5" paintOrder="stroke">
            r
          </text>
        </g>
        <g transform="translate(340, 330) rotate(-4)">
          <text x="0" y="0" fill={c1} stroke={lineCol} strokeWidth="5" paintOrder="stroke">
            y
          </text>
        </g>
      </g>

      {/* Sparkles */}
      <g stroke={lineCol} strokeWidth="2.5" strokeLinecap="round">
        <path d="M 50 50 l 0 -14 M 50 50 l 0 14 M 50 50 l -14 0 M 50 50 l 14 0" />
        <path d="M 360 60 l 0 -10 M 360 60 l 0 10 M 360 60 l -10 0 M 360 60 l 10 0" />
        <path d="M 40 260 l 0 -8 M 40 260 l 0 8 M 40 260 l -8 0 M 40 260 l 8 0" />
        <path d="M 370 280 l 0 -12 M 370 280 l 0 12 M 370 280 l -12 0 M 370 280 l 12 0" />
      </g>
    </svg>
  )
}
