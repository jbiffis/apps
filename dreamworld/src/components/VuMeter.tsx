interface VuMeterProps {
  value: number
  max: number
  height?: number
  showNumeric?: boolean
}

/**
 * Horizontal VU meter for vote counts. Fill width is value/max; the
 * track has subtle inset shadow + segmented overlay for that vintage
 * needle-meter feel.
 */
export default function VuMeter({
  value,
  max,
  height = 12,
  showNumeric = false,
}: VuMeterProps) {
  const pct = max <= 0 ? 0 : Math.max(0, Math.min(100, (value / max) * 100))
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
      }}
    >
      <div className="vu-track" style={{ flex: 1, height }}>
        <div
          className="vu-fill"
          style={{ width: `${pct}%` }}
          role="progressbar"
          aria-valuenow={value}
          aria-valuemin={0}
          aria-valuemax={max}
        />
      </div>
      {showNumeric && (
        <span
          className="mono"
          style={{
            fontSize: 11,
            fontWeight: 700,
            color: 'var(--orange-bright)',
            minWidth: 28,
            textAlign: 'right',
            textShadow: '0 0 6px var(--orange-glow)',
          }}
        >
          {value}
        </span>
      )}
    </div>
  )
}
