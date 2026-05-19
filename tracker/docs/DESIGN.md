# Design

Source of truth: `lifetracker-polished.html` from the Claude Design handoff. Reproduce visually; the React implementation doesn't have to copy the prototype's DOM structure.

## Palette

CSS variables (defined in `frontend/src/theme.css`). Light is `:root`, dark is `.dark` on `<html>`.

```css
:root {
  /* light — cream paper, shamrock + sky accents */
  --bg:         #f6efe4;   /* warm cream */
  --surface:    #ffffff;
  --surface-2:  #fceade;   /* powder petal — sparingly */
  --line:       #e0d4c0;
  --ink:        #310a31;   /* midnight violet */
  --ink-2:      #4d2a4d;
  --ink-3:      #7a627a;
  --accent:     #329f5b;   /* shamrock */
  --accent-2:   #d6efdf;   /* shamrock 10% */
  --accent-ink: #18472a;
  --accent-deep:#1f6b3d;
  --warn:       #c03221;   /* brick ember */
  --coral:      #c03221;
  --amber:      #e08b2d;
  --sky:        #2aa6c9;
  --plum:       #310a31;
}
.dark {
  --bg:         #1a051a;   /* deep midnight violet */
  --surface:    #2a0a2a;
  --surface-2:  #3a1540;
  --line:       #4a1f4a;
  --ink:        #fceade;   /* powder petal as type */
  --ink-2:      #e8c9b8;
  --ink-3:      #a58a9e;
  --accent:     #329f5b;   /* keep shamrock; sky aqua accents on cards */
  --accent-2:   #1a4a2a;
  --accent-ink: #d6efdf;
  --accent-deep:#1f6b3d;
  --warn:       #c03221;
  --coral:      #c03221;
  --amber:      #e08b2d;
  --sky:        #54defd;   /* sky aqua brightens for dark */
  --plum:       #310a31;
}
```

Tracker tile tints (`t-coral`, `t-amber`, `t-sky`, `t-plum`, `t-green`) are kept **uniform** in light mode per the final design decision. In dark mode they tint the icon background subtly.

## Typography

```html
<link href="https://fonts.googleapis.com/css2?family=Nunito:wght@400;500;600;700;800&family=DM+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
```

```css
--display: 'Nunito', system-ui, sans-serif;       /* headings, big values, FAB-adjacent */
--body:    'DM Sans', system-ui, sans-serif;      /* UI text, labels, buttons */
--mono:    'JetBrains Mono', ui-monospace, mono;  /* meta, timestamps, kicker labels */
```

Type scale (mobile):
| token | size / weight | use |
|---|---|---|
| `--t-display-xl` | Nunito 800 / 28px | greeting "Good morning, Alex." |
| `--t-display-lg` | Nunito 800 / 26px | detail screen H1 |
| `--t-display-md` | Nunito 800 / 22px | hero card big value |
| `--t-display-sm` | Nunito 700 / 15px | "All trackers" section header |
| `--t-body` | DM Sans 500 / 13px | default text |
| `--t-body-sm` | DM Sans 500 / 11px | tile labels |
| `--t-mono-xs` | JetBrains 400 / 10–11px | kickers, timestamps, "edit" link |

Letter-spacing `-0.01em` to `-0.02em` on display sizes.

## Layout primitives

### Phone shell
- **On mobile**: full-bleed, viewport height.
- **On desktop**: centered card, `max-width: 480px`, with the design's `border-radius: 44px` + shadow shell **for marketing/preview only**. Default desktop = same as mobile, just centered.

### App bar
- 56–60px tall, padding `8px 18px 10px`.
- Left/center/right pattern. Title uses `--display` 800/22px.
- Buttons are 36px circles, `--surface` background, `--line` border.

### Bottom nav (4 tabs: Home / Log / Stats / Me)
- Border-top `1px --line`, background `--bg`.
- Inactive: icon + label, `--ink-3`.
- Active: pill in `--accent-2` with icon + label in `--accent-ink`.

### Cards
- `qcard` — hero card. 20px radius, `--surface` bg, `--line` border, 14px 12px padding, min-height 148px.
- `qcard.primary` — gradient `160deg, --accent → --accent-deep`, white text. Used for the most actionable hero (e.g. Medication when meds are pending).
- `qcard.highlight` — used post-save: `--accent` border + `--accent-2` glow ring.
- Inner stack: icon (34px rounded square) → `.mid` block (name + value + sub) → progress `track` (5px pill).

### Tracker tiles (Home "All trackers" grid)
- 4-col grid, 54×54 rounded square icon, label below.
- Uniform `--surface` background, `--ink-2` icon, `--line` border (light mode).
- Dark mode adds a subtle category tint per `t-*` class.

### Fields (entry screen)
- 18px radius, `--surface` bg, `--line` border, 14px 16px padding.
- `field-lbl`: mono 10px, uppercase, `--ink-3`, letter-spacing `.08em`.
- `field-val`: display 700/18px.

### Dropdown-first picker (used on Medication)
- Trigger row with chevron in `--accent`.
- List items: 32px icon, bold name, mono "last: …" sub. Selected row uses `--accent-2` bg + check.
- "+ Add new …" row with dashed `+` square.

### Chips
- `padding: 6px 12px; border-radius: 999px`.
- Off: `--surface-2` bg, `--ink-2` text, `--line` border.
- `chip.on`: `--accent` bg, white text, no border, soft shadow.
- `white-space: nowrap;`

### Buttons
- 14px padding, 14px radius, display 700/15px.
- Secondary: `--surface` bg, `--line` border, `--ink` text.
- Primary: `linear-gradient(160deg, --accent, --accent-deep)`, white text, shadow.

### FAB
- 58px rounded square (20px radius), bottom-right above bottom nav (right 18, bottom 68).
- Gradient primary, soft accent shadow.

### Toast
- Absolute, `left/right 18px`, `bottom 92px` (above bottom nav).
- `--ink` bg, `--bg` text. 32px square icon in `--accent`, white check.
- Optional `Undo` link in `--accent`.

## Icon set

Ported from `polished-icons.jsx` to `frontend/src/icons/` as individual React components. Spec: 24px viewBox, 2px stroke, rounded line caps, `fill="none"`, `stroke="currentColor"`. Names match the prototype: `Home`, `Plus`, `Stats`, `User`, `Bell`, `Menu`, `Back`, `Close`, `Check`, `Chevron`, `Pill`, `Water`, `Sleep`, `Mood`, `Steps`, `Workout`, `Coffee`, `Food`, `Journal`, `Meditation`, `Weight`, `Heart`, `Clock`, `Music`, `Sun`, `Pin`, `Sparkle`.

`event_types.icon` stores the component name; a `<DynamicIcon name="Pill"/>` helper looks it up.

## Dark mode

- `.dark` class lives on `<html>`, toggled by `MeMenu` and persisted in `localStorage.tracker.theme` (`'light' | 'dark' | 'system'`).
- Default `'system'` — follows `prefers-color-scheme` until the user picks explicitly.
- All colors come from CSS variables — never hardcode hex in components.
- Test every new component in both modes before shipping.

## Motion (Phase 1: minimal)

- Toast fades in/out (200ms).
- Highlight pulse on the just-saved card (subtle, optional).
- No page transitions; no skeleton loaders (TanStack Query default loading state is fine).

## Accessibility

- Tap targets ≥ 44×44 px.
- All icons in buttons need `aria-label`.
- Color contrast ≥ 4.5:1 for body text against backgrounds in both modes (the palette is tuned for this; verify when adding new colors).
- Form inputs paired with `<label>`; chips grouped with `role="radiogroup"` / `role="group"` as appropriate.
