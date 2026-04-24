import { useState } from 'react'
import BookStoryLogo from './BookStoryLogo'
import Icon from './Icon'
import OwlMascot from './OwlMascot'
import PaperScraps from './PaperScraps'
import { AVATAR_CHOICES, useProfile } from '../lib/profile'

interface OnboardingProps {
  onDone: () => void
}

export default function Onboarding({ onDone }: OnboardingProps) {
  const { update } = useProfile()
  const [step, setStep] = useState(0)
  const [name, setName] = useState('')
  const [avatar, setAvatar] = useState<string>('🦊')

  const totalSteps = 3

  function next() {
    if (step === 0) {
      const n = name.trim()
      if (!n) return
    }
    if (step < totalSteps - 1) {
      setStep(step + 1)
    } else {
      update({
        name: name.trim() || 'friend',
        avatar,
        onboarded: true,
      })
      onDone()
    }
  }

  function back() {
    if (step > 0) setStep(step - 1)
  }

  return (
    <div
      className="paper-bg"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 150,
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <PaperScraps />

      {/* Progress dots */}
      <div
        style={{
          position: 'sticky',
          top: 0,
          display: 'flex',
          justifyContent: 'center',
          gap: 8,
          padding: '16px 0 0',
          zIndex: 2,
        }}
      >
        {Array.from({ length: totalSteps }).map((_, i) => (
          <div
            key={i}
            style={{
              width: i === step ? 28 : 10,
              height: 10,
              borderRadius: 6,
              background:
                i === step
                  ? 'var(--accent-1)'
                  : i < step
                    ? 'var(--accent-2)'
                    : 'var(--bg-2)',
              border: '2px solid var(--line)',
              transition: 'width 0.2s ease',
            }}
          />
        ))}
      </div>

      <div
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          padding: '24px 20px',
          justifyContent: 'center',
          minHeight: 0,
        }}
      >
        {step === 0 && (
          <StepName
            name={name}
            setName={setName}
            avatar={avatar}
            setAvatar={setAvatar}
          />
        )}
        {step === 1 && <StepAdd />}
        {step === 2 && <StepGamification name={name || 'friend'} />}
      </div>

      {/* Footer nav */}
      <div
        style={{
          padding: '12px 20px calc(20px + env(safe-area-inset-bottom, 0px))',
          display: 'flex',
          gap: 10,
          alignItems: 'center',
          borderTop: '2px solid var(--line)',
          background: 'var(--paper)',
        }}
      >
        {step > 0 ? (
          <button onClick={back} className="btn btn-ghost">
            <Icon name="arrow-left" size={14} /> Back
          </button>
        ) : (
          <div style={{ flex: 1 }} />
        )}
        <div style={{ flex: 1 }} />
        <button
          onClick={next}
          className="btn btn-primary"
          disabled={step === 0 && !name.trim()}
        >
          {step < totalSteps - 1 ? (
            <>
              Next <Icon name="arrow-right" size={14} stroke="#fff" />
            </>
          ) : (
            <>
              Let's go! <Icon name="sparkle" size={14} stroke="#fff" />
            </>
          )}
        </button>
      </div>
    </div>
  )
}

function StepName({
  name,
  setName,
  avatar,
  setAvatar,
}: {
  name: string
  setName: (v: string) => void
  avatar: string
  setAvatar: (v: string) => void
}) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          marginBottom: 8,
        }}
      >
        <BookStoryLogo size={200} />
      </div>
      <div
        style={{
          fontSize: 12,
          fontWeight: 800,
          letterSpacing: '0.1em',
          textTransform: 'uppercase',
          color: 'var(--ink-mute)',
          marginTop: 12,
        }}
      >
        Welcome to your clubhouse
      </div>
      <h1 className="serif" style={{ fontSize: 28, margin: '4px 0 12px', lineHeight: 1.1 }}>
        What should we call you?
      </h1>
      <input
        className="input"
        type="text"
        autoFocus
        autoComplete="given-name"
        maxLength={24}
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="Your first name"
        style={{
          textAlign: 'center',
          fontSize: 20,
          fontFamily: 'Fraunces, serif',
          fontWeight: 700,
          marginBottom: 18,
        }}
      />
      <div
        style={{
          fontSize: 11,
          fontWeight: 800,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
          color: 'var(--ink-mute)',
          marginBottom: 10,
        }}
      >
        Pick your sidekick
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(6, 1fr)',
          gap: 8,
          maxWidth: 360,
          margin: '0 auto',
        }}
      >
        {AVATAR_CHOICES.map((a) => {
          const active = avatar === a
          return (
            <button
              key={a}
              onClick={() => setAvatar(a)}
              style={{
                width: '100%',
                aspectRatio: '1 / 1',
                borderRadius: '50%',
                border: '2px solid var(--line)',
                background: active ? 'var(--accent-2)' : 'var(--paper)',
                fontSize: 24,
                cursor: 'pointer',
                boxShadow: active ? 'var(--shadow-sm)' : 'none',
                padding: 0,
              }}
              aria-label={`Pick ${a}`}
              aria-pressed={active}
            >
              {a}
            </button>
          )
        })}
      </div>
    </div>
  )
}

function StepAdd() {
  return (
    <div>
      <div style={{ textAlign: 'center', marginBottom: 18 }}>
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 6 }}>
          <OwlMascot size={86} mood="happy" />
        </div>
        <h1 className="serif" style={{ fontSize: 28, lineHeight: 1.1 }}>
          Adding books is a breeze 🌬️
        </h1>
        <p
          style={{
            fontSize: 14,
            fontWeight: 700,
            color: 'var(--ink-soft)',
            marginTop: 6,
            padding: '0 12px',
          }}
        >
          Tap the big <strong style={{ color: 'var(--accent-1)' }}>+</strong>{' '}
          at the bottom of every screen, then…
        </p>
      </div>

      <div
        className="card"
        style={{
          padding: 14,
          marginBottom: 10,
          background: 'var(--accent-3)',
          color: '#fff',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
        }}
      >
        <div
          style={{
            width: 46,
            height: 46,
            borderRadius: '50%',
            background: 'rgba(255,255,255,0.25)',
            border: '2px solid var(--line)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Icon name="camera" size={22} stroke="#fff" />
        </div>
        <div>
          <div className="serif" style={{ fontSize: 17, fontWeight: 700 }}>
            Scan the barcode
          </div>
          <div style={{ fontSize: 12, fontWeight: 700, opacity: 0.9 }}>
            Fastest — point your camera at the back of the book
          </div>
        </div>
      </div>

      <div
        className="card"
        style={{
          padding: 14,
          marginBottom: 10,
          background: 'var(--accent-2)',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
        }}
      >
        <div
          style={{
            width: 46,
            height: 46,
            borderRadius: '50%',
            background: 'var(--paper)',
            border: '2px solid var(--line)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Icon name="edit" size={20} />
        </div>
        <div>
          <div className="serif" style={{ fontSize: 17, fontWeight: 700 }}>
            Type it in
          </div>
          <div style={{ fontSize: 12, fontWeight: 700 }}>
            No camera? Type the ISBN from the back (starts with 978)
          </div>
        </div>
      </div>

      <div
        className="card rotate-sm-r"
        style={{
          padding: 12,
          background: 'var(--sticker-pink)',
          textAlign: 'center',
          marginTop: 16,
        }}
      >
        <p style={{ margin: 0, fontSize: 13, fontWeight: 700 }}>
          Then pick a shelf: <strong>Reading</strong> 📖,{' '}
          <strong>Up next</strong> 🔖, <strong>Wishlist</strong> 💭, or{' '}
          <strong>Finished</strong> ⭐
        </p>
      </div>
    </div>
  )
}

function StepGamification({ name }: { name: string }) {
  return (
    <div>
      <div style={{ textAlign: 'center', marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 6 }}>
          <div
            className="sticker wobble"
            style={{
              width: 80,
              height: 80,
              background: 'var(--sticker-yellow)',
              fontSize: 40,
              boxShadow: 'var(--shadow)',
            }}
          >
            🏆
          </div>
        </div>
        <h1 className="serif" style={{ fontSize: 26, lineHeight: 1.1 }}>
          Go on a reading quest
        </h1>
        <p
          style={{
            fontSize: 14,
            fontWeight: 700,
            color: 'var(--ink-soft)',
            marginTop: 6,
            padding: '0 12px',
          }}
        >
          The more you read, {name}, the more you unlock!
        </p>
      </div>

      <div
        className="card rotate-sm"
        style={{
          padding: 14,
          marginBottom: 10,
          background: 'var(--accent-3)',
          color: '#fff',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <Icon name="trophy" size={28} stroke="#fff" />
          <div>
            <div
              style={{
                fontSize: 10,
                fontWeight: 800,
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                opacity: 0.85,
              }}
            >
              The quest
            </div>
            <div className="serif" style={{ fontSize: 18, fontWeight: 700 }}>
              20 books this year
            </div>
          </div>
        </div>
        <div
          style={{
            display: 'flex',
            gap: 3,
            flexWrap: 'wrap',
            marginTop: 10,
          }}
        >
          {Array.from({ length: 20 }).map((_, i) => (
            <div
              key={i}
              style={{
                width: 12,
                height: 18,
                background: 'rgba(255,255,255,0.18)',
                border: '1.5px solid rgba(255,255,255,0.4)',
                borderRadius: '2px 3px 3px 2px',
              }}
            />
          ))}
        </div>
      </div>

      <div
        className="card"
        style={{
          padding: 14,
          marginBottom: 10,
        }}
      >
        <div className="serif" style={{ fontSize: 16, marginBottom: 8 }}>
          Earn sticker badges
        </div>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: 10,
          }}
        >
          {[
            { emoji: '🌱', bg: 'var(--sticker-green)', rot: 'rotate-sm' },
            { emoji: '🖐️', bg: 'var(--sticker-yellow)', rot: 'rotate-sm-r' },
            { emoji: '🐛', bg: 'var(--sticker-green)', rot: 'rotate-sm' },
            { emoji: '🐉', bg: 'var(--sticker-pink)', rot: 'rotate-sm-r' },
          ].map((b, i) => (
            <div
              key={i}
              className={b.rot}
              style={{
                display: 'flex',
                justifyContent: 'center',
              }}
            >
              <div
                className="sticker"
                style={{
                  width: 52,
                  height: 52,
                  background: b.bg,
                  fontSize: 22,
                  boxShadow: 'var(--shadow-sm)',
                }}
              >
                {b.emoji}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div
        className="card rotate-sm-r"
        style={{
          padding: 12,
          background: 'var(--sticker-pink)',
          textAlign: 'center',
        }}
      >
        <p style={{ margin: 0, fontSize: 13, fontWeight: 700 }}>
          Write reviews, rate books, finish long ones — every page counts! 🐛
        </p>
      </div>
    </div>
  )
}
