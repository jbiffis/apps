import type { SVGProps } from 'react'

export type IconName =
  | 'home'
  | 'shelf'
  | 'plus'
  | 'trophy'
  | 'user'
  | 'search'
  | 'star'
  | 'check'
  | 'arrow-right'
  | 'arrow-left'
  | 'book-open'
  | 'bookmark'
  | 'sparkle'
  | 'flame'
  | 'heart'
  | 'close'
  | 'camera'
  | 'edit'
  | 'filter'
  | 'trash'

interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  name: IconName
  size?: number
  strokeWidth?: number
}

export default function Icon({
  name,
  size = 22,
  stroke = 'currentColor',
  fill = 'none',
  strokeWidth = 2.2,
  ...rest
}: IconProps) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill,
    stroke,
    strokeWidth,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    ...rest,
  }
  switch (name) {
    case 'home':
      return (
        <svg {...common}>
          <path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z" />
        </svg>
      )
    case 'shelf':
      return (
        <svg {...common}>
          <rect x="3.5" y="4" width="4" height="16" rx="0.5" />
          <rect x="9" y="6" width="4" height="14" rx="0.5" />
          <rect x="14.5" y="3" width="4" height="17" rx="0.5" />
          <path d="M2 21h20" />
        </svg>
      )
    case 'plus':
      return (
        <svg {...common}>
          <path d="M12 5v14M5 12h14" />
        </svg>
      )
    case 'trophy':
      return (
        <svg {...common}>
          <path d="M8 4h8v5a4 4 0 0 1-8 0V4z" />
          <path d="M8 6H5a2 2 0 0 0 0 4h3M16 6h3a2 2 0 0 1 0 4h-3" />
          <path d="M10 13v3M14 13v3M8 19h8M9 16h6v3H9z" />
        </svg>
      )
    case 'user':
      return (
        <svg {...common}>
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21c0-4 4-6 8-6s8 2 8 6" />
        </svg>
      )
    case 'search':
      return (
        <svg {...common}>
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.5-3.5" />
        </svg>
      )
    case 'star':
      return (
        <svg {...common}>
          <path d="M12 3l2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 17.8 6.2 20.9l1.1-6.5L2.6 9.8l6.5-.9z" />
        </svg>
      )
    case 'check':
      return (
        <svg {...common}>
          <path d="M4 12l5 5L20 6" />
        </svg>
      )
    case 'arrow-right':
      return (
        <svg {...common}>
          <path d="M5 12h14M13 6l6 6-6 6" />
        </svg>
      )
    case 'arrow-left':
      return (
        <svg {...common}>
          <path d="M19 12H5M11 18l-6-6 6-6" />
        </svg>
      )
    case 'book-open':
      return (
        <svg {...common}>
          <path d="M12 6c-2-1.5-5-2-8-2v14c3 0 6 0.5 8 2 2-1.5 5-2 8-2V4c-3 0-6 0.5-8 2z" />
          <path d="M12 6v14" />
        </svg>
      )
    case 'bookmark':
      return (
        <svg {...common}>
          <path d="M6 3h12v18l-6-4-6 4z" />
        </svg>
      )
    case 'sparkle':
      return (
        <svg {...common}>
          <path d="M12 3v6M12 15v6M3 12h6M15 12h6M6 6l3 3M15 15l3 3M18 6l-3 3M9 15l-3 3" />
        </svg>
      )
    case 'flame':
      return (
        <svg {...common}>
          <path d="M12 3c1 3-3 5-3 9a5 5 0 0 0 10 0c0-2-1-3-2-5-0.5 1-1 1.5-2 1.5 0-2 0-4-3-5.5z" />
        </svg>
      )
    case 'heart':
      return (
        <svg {...common}>
          <path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z" />
        </svg>
      )
    case 'close':
      return (
        <svg {...common}>
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      )
    case 'camera':
      return (
        <svg {...common}>
          <path d="M3 7h4l2-2h6l2 2h4v12H3z" />
          <circle cx="12" cy="13" r="3.5" />
        </svg>
      )
    case 'edit':
      return (
        <svg {...common}>
          <path d="M14 4l6 6-11 11H3v-6z" />
          <path d="M13 5l6 6" />
        </svg>
      )
    case 'filter':
      return (
        <svg {...common}>
          <path d="M3 5h18M6 12h12M10 19h4" />
        </svg>
      )
    case 'trash':
      return (
        <svg {...common}>
          <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" />
        </svg>
      )
    default:
      return null
  }
}
