// Icon set — 24px viewBox, 2px stroke, rounded caps, fill:none,
// stroke:currentColor (so color comes from the parent's text color). Names
// match the prototype / event_types.icon values. Add new icons here and they
// become available to <DynamicIcon name="…" />.

function Svg({ children, size = 24, ...rest }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...rest}
    >
      {children}
    </svg>
  )
}

export const Home = (p) => (
  <Svg {...p}>
    <path d="M3 10.5 12 3l9 7.5" />
    <path d="M5 9.5V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5" />
    <path d="M9.5 21v-6h5v6" />
  </Svg>
)

export const Plus = (p) => (
  <Svg {...p}>
    <path d="M12 5v14M5 12h14" />
  </Svg>
)

export const Stats = (p) => (
  <Svg {...p}>
    <path d="M4 20V10M10 20V4M16 20v-6M22 20H2" />
  </Svg>
)

export const User = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4 21c0-4 4-6 8-6s8 2 8 6" />
  </Svg>
)

export const Bell = (p) => (
  <Svg {...p}>
    <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
    <path d="M10.5 21a2 2 0 0 0 3 0" />
  </Svg>
)

export const Menu = (p) => (
  <Svg {...p}>
    <path d="M4 6h16M4 12h16M4 18h16" />
  </Svg>
)

export const Back = (p) => (
  <Svg {...p}>
    <path d="M15 5l-7 7 7 7" />
  </Svg>
)

export const Close = (p) => (
  <Svg {...p}>
    <path d="M6 6l12 12M18 6 6 18" />
  </Svg>
)

export const Check = (p) => (
  <Svg {...p}>
    <path d="M5 12.5 10 17l9-10" />
  </Svg>
)

export const Chevron = (p) => (
  <Svg {...p}>
    <path d="M9 6l6 6-6 6" />
  </Svg>
)

export const Pill = (p) => (
  <Svg {...p}>
    <rect x="3" y="8" width="18" height="8" rx="4" transform="rotate(45 12 12)" />
    <path d="M8.5 8.5 15.5 15.5" />
  </Svg>
)

export const Water = (p) => (
  <Svg {...p}>
    <path d="M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11Z" />
  </Svg>
)

export const Sleep = (p) => (
  <Svg {...p}>
    <path d="M20 14.5A8 8 0 1 1 9.5 4 6.5 6.5 0 0 0 20 14.5Z" />
  </Svg>
)

export const Mood = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M8.5 14.5a4.5 4.5 0 0 0 7 0" />
    <path d="M9 9.5h.01M15 9.5h.01" />
  </Svg>
)

export const Steps = (p) => (
  <Svg {...p}>
    <path d="M7 4c2 0 3 2 3 5s-1 4-1 6 1 3-1 4-3-1-3-4 1-3 1-6-1-5 2-5Z" />
    <path d="M17 8c2 0 3 2 3 5s-1 4-1 5 .5 2-1 2-2-1-2-3 .5-3 .5-5S15 8 17 8Z" />
  </Svg>
)

export const Workout = (p) => (
  <Svg {...p}>
    <path d="M4 9v6M7 7v10M17 7v10M20 9v6M7 12h10" />
  </Svg>
)

export const Coffee = (p) => (
  <Svg {...p}>
    <path d="M4 8h13v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V8Z" />
    <path d="M17 9h2.5a2.5 2.5 0 0 1 0 5H17" />
    <path d="M8 3v2M12 3v2" />
  </Svg>
)

export const Food = (p) => (
  <Svg {...p}>
    <path d="M6 3v8a2 2 0 0 0 4 0V3M8 11v10" />
    <path d="M16 3c-1.5 0-2.5 2-2.5 5S15 13 16 13s2.5-2 2.5-5S17.5 3 16 3Zm0 10v8" />
  </Svg>
)

export const Journal = (p) => (
  <Svg {...p}>
    <path d="M6 3h11a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" />
    <path d="M4 17h14M8.5 8h6M8.5 11.5h6" />
  </Svg>
)

export const Meditation = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="5" r="2" />
    <path d="M12 8c0 3 4 3 8 5-4 1-5 4-8 4s-4-3-8-4c4-2 8-2 8-5Z" />
  </Svg>
)

export const Weight = (p) => (
  <Svg {...p}>
    <rect x="3" y="5" width="18" height="14" rx="3" />
    <path d="M12 9v3M9 12h6" />
  </Svg>
)

export const Heart = (p) => (
  <Svg {...p}>
    <path d="M12 20s-7-4.5-9-9a4.5 4.5 0 0 1 9-2 4.5 4.5 0 0 1 9 2c-2 4.5-9 9-9 9Z" />
  </Svg>
)

export const Clock = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </Svg>
)

export const Music = (p) => (
  <Svg {...p}>
    <path d="M9 18V5l11-2v13" />
    <circle cx="6" cy="18" r="3" />
    <circle cx="17" cy="16" r="3" />
  </Svg>
)

export const Sun = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
  </Svg>
)

export const Pin = (p) => (
  <Svg {...p}>
    <path d="M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11Z" />
    <circle cx="12" cy="10" r="2.5" />
  </Svg>
)

export const Sparkle = (p) => (
  <Svg {...p}>
    <path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z" />
  </Svg>
)

export const icons = {
  Home, Plus, Stats, User, Bell, Menu, Back, Close, Check, Chevron,
  Pill, Water, Sleep, Mood, Steps, Workout, Coffee, Food, Journal,
  Meditation, Weight, Heart, Clock, Music, Sun, Pin, Sparkle,
}

// Look up an icon by its stored name (event_types.icon). Falls back to a
// neutral mark so an unknown name never crashes a screen.
export function DynamicIcon({ name, fallback = 'Sparkle', ...rest }) {
  const Cmp = icons[name] || icons[fallback]
  return <Cmp {...rest} />
}
