# LifeTracker frontend

React 19 + Vite SPA, served at `https://apps.biffis.com/tracker/`.

## Dev

```bash
npm install
npm run dev      # Vite at http://localhost:5173/tracker/
                 # proxies /tracker/api → http://localhost:8080/api (run the backend separately)
npm run lint
npm run build    # outputs dist/ (committed)
npm run icons    # regenerate PWA icons from public/favicon.svg
```

## Deploy

There is **no frontend container.** `dist/` is committed and bind-mounted into the
shared Apache `web` service (see `../../compose.yaml`) at `/var/www/html/tracker`;
`../../tracker-proxy.conf` handles SPA fallback + the `/tracker/api/` reverse proxy.
After changing the frontend, `npm run build` and commit `dist/` so the restart-only
Komodo deploy picks it up. Deploying the prod stack is the owner's job (the agent
is fenced out of the tracker Komodo stack).

## Layout

- `src/theme.css` / `src/theme.js` — palette tokens (light `:root` + `.dark`) and
  the light/dark/system toggle (`localStorage tracker.theme`). See `../docs/DESIGN.md`.
- `src/api.js` — base-relative, JWT-aware fetch client (`Authorization: Bearer`,
  401 clears the session). `src/auth.js` — token/user storage + JWT decode.
- `src/icons/index.jsx` — the icon set + `<DynamicIcon name="…" />` for
  `event_types.icon` values.
- `src/App.jsx` — Epic 5 placeholder; real screens land in Epics 6+.
