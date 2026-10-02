# ChatterLand frontend

Next.js App Router frontend for the ChatterLand language rehabilitation service. Source is organized by feature (`auth`, `student`, `teacher`), with shared API, components, assets, and design tokens following the TripMate project conventions while retaining Next.js routing.

## Run locally

```sh
npm install
cp .env.example .env.local
npm run dev
```

The backend defaults to `http://localhost:8080`; set `NEXT_PUBLIC_API_BASE_URL` in `.env.local` to change it. Start the MariaDB-backed Spring Boot service from `../backend` before using authenticated screens.

Production build: `npm run build`.
