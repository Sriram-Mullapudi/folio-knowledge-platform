# Folio project instructions

## Scope

Work only in this project repository. Its parent home directory is a separate Git repository and must not be staged or pushed as part of Folio work.

## User-authorized GitHub workflow

The user asked to save the current project and future work to their GitHub account. At the end of a completed implementation task in this repository, run relevant checks, review the diff and staged paths for secrets or unrelated files, commit the task changes, and push the current project branch to its configured origin. Do not ask again for routine commit/push approval within this scope. If a task is unfinished or verification fails, report that clearly instead of claiming it is complete. Do not force-push, overwrite remote work, change repository visibility, or merge unrelated work without appropriate authorization.

This is a workflow preference for active work, not a scheduled background backup. Keep the repository private unless the user requests otherwise. Create new branches with the codex/ prefix when a branch is needed.

## Data and credentials

Never commit .env.local, provider credentials, private keys, runtime logs, database dumps, uploaded user documents, dependency folders, or downloaded model weights. Use fictional fixtures for tests. GitHub preserves project code and documentation, not the user's live document database or model storage.

## Verification

Backend: run relevant Maven tests from backend (Java 17, Docker for integration tests). Frontend: npm run build and npm run lint from frontend. Check meaningful UI changes in the browser using the synthetic account, and do not modify the user's documents for testing. Never claim live AI behavior from mocked tests alone.
