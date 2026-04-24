import { useEffect, useState } from 'react'
import BookStoryLogo from './BookStoryLogo'
import OwlMascot from './OwlMascot'
import PaperScraps from './PaperScraps'

interface SplashScreenProps {
  onDone: () => void
  duration?: number
}

export default function SplashScreen({
  onDone,
  duration = 2200,
}: SplashScreenProps) {
  const [exiting, setExiting] = useState(false)

  useEffect(() => {
    const t1 = setTimeout(() => setExiting(true), duration)
    const t2 = setTimeout(() => onDone(), duration + 600)
    return () => {
      clearTimeout(t1)
      clearTimeout(t2)
    }
  }, [duration, onDone])

  return (
    <div
      className="paper-bg"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 200,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        opacity: exiting ? 0 : 1,
        transform: exiting ? 'scale(1.04)' : 'scale(1)',
        transition: 'opacity 0.55s ease, transform 0.55s ease',
        padding: 24,
      }}
    >
      <PaperScraps />
      <div
        style={{
          position: 'absolute',
          top: 48,
          left: 40,
          width: 16,
          height: 16,
          background: 'var(--sticker-pink)',
          transform: 'rotate(15deg)',
          border: '2px solid var(--line)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          top: 96,
          right: 60,
          width: 12,
          height: 12,
          background: 'var(--sticker-blue)',
          borderRadius: '50%',
          border: '2px solid var(--line)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: 120,
          left: 56,
          width: 20,
          height: 7,
          background: 'var(--sticker-green)',
          transform: 'rotate(-20deg)',
          border: '2px solid var(--line)',
          borderRadius: 3,
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: 160,
          right: 72,
          width: 14,
          height: 14,
          background: 'var(--sticker-yellow)',
          transform: 'rotate(25deg)',
          border: '2px solid var(--line)',
        }}
      />

      <div className="float pop-in" style={{ marginBottom: 4 }}>
        <OwlMascot size={96} mood="reading" />
      </div>

      <div
        className="pop-in"
        style={{ animationDelay: '0.15s', animationFillMode: 'backwards' }}
      >
        <BookStoryLogo size={260} />
      </div>

      <div
        className="pop-in"
        style={{
          marginTop: 8,
          fontFamily: 'Nunito, sans-serif',
          fontSize: 14,
          fontWeight: 800,
          color: 'var(--ink-soft)',
          padding: '8px 16px',
          background: 'var(--paper)',
          border: '2px solid var(--line)',
          borderRadius: 999,
          boxShadow: 'var(--shadow-sm)',
          transform: 'rotate(-2deg)',
          animationDelay: '0.35s',
          animationFillMode: 'backwards',
        }}
      >
        🐛 every page is an adventure
      </div>

      <div
        style={{
          position: 'absolute',
          bottom: 40,
          display: 'flex',
          gap: 8,
        }}
      >
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            style={{
              width: 10,
              height: 10,
              borderRadius: '50%',
              background: 'var(--accent-1)',
              border: '2px solid var(--line)',
              animation: `float 1s ease-in-out ${i * 0.15}s infinite`,
            }}
          />
        ))}
      </div>
    </div>
  )
}
