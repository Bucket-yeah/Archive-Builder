# Origins Chaos Addon — Web Workspace

A Node.js/Express API server and React mockup sandbox supporting the Origins Chaos Addon Minecraft mod (NeoForge 1.21.1). The web workspace provides a typed API layer, PostgreSQL database, and UI tooling for the mod project.

## Run & Operate

- `PORT=5000 pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string (auto-provisioned by Replit)

## Stack

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM (Replit managed)
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `artifacts/api-server/` — Express API server
- `artifacts/mockup-sandbox/` — React/Vite UI sandbox
- `lib/db/` — Drizzle schema and DB client (`DATABASE_URL` from env)
- `lib/api-spec/` — OpenAPI YAML definition (source of truth for API)
- `lib/api-zod/` — Zod schemas generated from OpenAPI spec
- `lib/api-client-react/` — React Query hooks generated from OpenAPI spec
- `chaos_addon_output/` — Minecraft mod source (Java/NeoForge)

## Architecture decisions

- Database uses Replit's managed PostgreSQL — `DATABASE_URL` is auto-set, no manual provisioning needed.
- API server runs on port 5000 (webview port) with `PORT=5000` set in the workflow.
- All routes are under `/api/*` prefix; `/api/healthz` returns `{"status":"ok"}`.
- Schema is empty by default — add tables to `lib/db/src/schema/index.ts` and run `pnpm --filter db run push`.
- The Minecraft mod is built separately via `bash chaos_addon_output/build.sh` (outputs a `.jar`).

## Product

Web infrastructure supporting a NeoForge Minecraft mod. Provides a typed REST API with OpenAPI spec, a shared database layer, and a React sandbox for UI prototyping.

## User preferences

_Populate as you build — explicit user instructions worth remembering across sessions._

## Gotchas

- Always run `pnpm install` after adding new packages to the workspace.
- The `preinstall` script enforces pnpm-only — never run `npm install` or `yarn`.
- `DATABASE_URL` is set automatically by Replit's database integration; do not hardcode it.
- `pnpm --filter db run push` applies schema changes to the dev database only; production schema is managed via Replit's Publish flow.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details.
- DB schema lives in `lib/db/src/schema/index.ts`.
- API contract lives in `lib/api-spec/`.
