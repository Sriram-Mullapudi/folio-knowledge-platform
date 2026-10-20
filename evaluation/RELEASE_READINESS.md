# Folio local release checks — 19 October 2026

## Verified

- Live hybrid-search answer suite: **8/8 automated checks passed** on six
  fictional documents. Covers policy facts, old/current policy comparison,
  project owner/date, ordered incident steps, missing information, unrelated
  questions, and malicious instructions embedded in an attachment.
- Full responses, source passages, checks, and timing are retained in
  `release-answer-results.json`. This run uses the local qwen2.5:3b answer model
  and nomic-embed-text:v1.5 embeddings.
- Six frontend tests cover session retention during outages, invalid refresh
  rejection, preserving an already open workspace, manual reconnection, and
  distinguishing an unhealthy service from a ready one, and upload completion
  with a recoverable error when token refresh fails.
- Frontend production build and lint pass. Upgraded Vite, Vitest, Tailwind and
  TypeScript ESLint tooling; npm audit reports zero known advisories for the
  resolved frontend dependency tree at verification time. CI uses Node 24,
  deterministic npm ci, and the new recovery tests.
- One-click launcher reuses a healthy workspace, starts stopped app processes,
  waits for the frontend-to-backend health check, and keeps logs outside Git.
  Port probes reject occupied ports. No data-reset command is used.
- Controlled occupied-database-port test passed: gracefully stopped the database,
  held its original port, and ran the launcher. It selected an available port,
  restarted with the existing volume, and passed existing-account login through
  the page proxy. A subsequent launch reused the healthy services.

## Still requires broader evidence

- Eight fictional cases are not a general answer-accuracy score. Automated
  phrase and citation-number checks do not prove every claim is supported.
- Varied PDFs, tables, scanned documents/OCR, long conversations, accessibility
  and user testing need a larger evaluation set.
- Current-page visual review is blocked by the browser tool's URL policy.
  DOM unit tests and a successful CSS build do not substitute for visual QA,
  particularly after the Tailwind migration.
- This is a local application. It needs Docker and local services while in use;
  it is not a hosted production deployment, uptime guarantee, or independent
  security audit. Saved refresh tokens still use localStorage.

## Reproduce

1. Double-click `Start Folio.cmd`; wait for the ready message.
2. In `frontend`: `npm ci`, `npm test`, `npm run build`, `npm run lint`, `npm audit`.
3. From the project root:
   `python evaluation/answer_eval.py --output evaluation/release-answer-results.json`.
   This creates synthetic accounts/documents and requires the local AI models.
