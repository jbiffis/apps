# Webapps

Simple PHP web applications served via Apache in Docker.

## Architecture

- **Dockerfile** — PHP 8.3 + Apache image, copies all app folders into `/var/www/html/`
- **compose.yaml** — Docker Compose config, maps port 8081
- Each app lives in its own folder (e.g., `beer/`)

## Deployment

Deployed via **Komodo** on a remote server. Komodo pulls from `git@github.com:jbiffis/apps.git` and restarts the container.

**Important:** Komodo does NOT rebuild the Docker image on deploy — it only restarts the container. PHP source files are served via bind mount (`./beer:/var/www/html/beer`), so code changes take effect on pull + restart. However, changes to `Dockerfile` or `apache-security.conf` require a manual image rebuild.

## Beer Order App (`beer/`)

Easter beer order form for Dépanneur Rapido specials.

### Files

- `beers.php` — Shared beer data, helper functions, CSRF/validation utilities
- `index.php` — Order form with live-updating totals
- `confirm.php` — Order confirmation and save
- `admin.php` — Password-protected admin view (orders, payment tracking, summary)
- Orders stored in `orders.json` at `/var/www/data/beer/` (outside web root, bind-mounted from `/var/lib/docker/volumes/apps/beer/` on host)

### Environment Variables

- `ADMIN_PASSWORD` — Required for admin page access

### Adding Beers

Beer IDs are array indexes in `beers.php`. **Always append new beers to the end of the array** to avoid breaking existing orders that reference IDs.

## Book-Log App workflow

When making changes to the `book-log/` app on behalf of the owner's daughter, after committing and pushing to the feature branch, also merge the feature branch into `main` and push `main` so the auto-deploy is triggered. Sequence: commit on feature branch → push feature branch → checkout main → pull → merge feature branch (no-ff) → push main → checkout feature branch.
