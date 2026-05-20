/** @type {import('tailwindcss').Config} */
// Colors map to the CSS variables defined in src/theme.css so that every
// utility (bg-bg, text-ink, border-line, …) flips automatically between light
// and dark via the `.dark` class on <html>. Never hardcode hex in components.
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        bg: 'var(--bg)',
        surface: 'var(--surface)',
        'surface-2': 'var(--surface-2)',
        line: 'var(--line)',
        ink: 'var(--ink)',
        'ink-2': 'var(--ink-2)',
        'ink-3': 'var(--ink-3)',
        accent: 'var(--accent)',
        'accent-2': 'var(--accent-2)',
        'accent-ink': 'var(--accent-ink)',
        'accent-deep': 'var(--accent-deep)',
        warn: 'var(--warn)',
        coral: 'var(--coral)',
        amber: 'var(--amber)',
        sky: 'var(--sky)',
        plum: 'var(--plum)',
      },
      fontFamily: {
        display: ['Nunito', 'system-ui', 'sans-serif'],
        body: ['"DM Sans"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      borderRadius: {
        qcard: '20px',
        field: '18px',
      },
    },
  },
  plugins: [],
}
