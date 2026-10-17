# Development work log

## October 14, 2026 — Source baseline and first regression fixes

### Source
Copied `Downloads/enterprise-knowledge-platform (3)` into `C:/Users/srira/enterprise-knowledge-platform`, excluding build outputs, dependencies, Git metadata, and local environment files. The original folder is unchanged. The owner confirmed that this is the available source.

Preserved the supplied brief in PROJECT_IDEA_ORIGINAL.txt and created PROJECT_SPEC.md. This checkout has five migrations and vector-only retrieval; the brief describes a later target state. Updated the README introduction to reflect the actual implementation.

### Implemented
- Added 12 unit test cases for document service ownership: metadata, chunks, download, delete, and reprocess reject another owner's document for both USER and ADMIN callers; denied operations do not invoke storage or other downstream services. Also covered owner access and list pagination bounds.
- Added an HTTP integration case covering cross-owner reads, reprocessing, deletion, list visibility, and continued owner access. This test compiled but has not run successfully without Docker.
- Fixed context assembly when a citation header alone exceeds the context budget. The assembler now excludes that passage instead of exceeding the limit; negative budgets are rejected. Added three regression cases for oversized headers, zero budgets, and negative budgets.

### Verification
Environment: Java 17, Maven wrapper, Windows.

Selected tests passed: **35 tests, 0 failures, 0 errors, 0 skipped**. This includes mocked provider tests; it does not call a paid model.

Reproduce from the backend directory in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\mvnw.cmd -B '-Dtest=DocumentAuthorizationTest,ContextAssemblerTest,SearchServiceTest,VectorsTest,RecursiveChunkerTest,OpenAiChatClientTest' test
```

The initial full suite compiled but reported 25 tests with 5 errors because Testcontainers could not find a running Docker daemon. The full suite has not passed, and the new HTTP isolation case remains unexecuted. Service-level Mockito checks do not prove database isolation or complete HTTP security.

### Remaining validation and next development
1. Make Docker available and rerun the complete integration suite against isolated test containers.
2. Verify the frontend build and run the local upload-to-search flow.
3. Implement owner-scoped keyword retrieval, its database migration, deterministic Reciprocal Rank Fusion, and fallback without embeddings.
4. Add bounded agent strategies after retrieval is verified.

No live OpenAI calls, cloud deployment, performance benchmark, or GitHub publication was performed in this pass. Production readiness is not established.

### Follow-up verification
- Frontend dependencies installed and package-lock.json generated for repeatable installs.
- Frontend production build passed (TypeScript check plus Vite, 41 modules transformed). An initial sandbox file-access error was resolved by running the same build outside that restriction.
- Docker API check failed because the Docker Desktop Linux engine pipe is unavailable. Full application startup, database-backed tests, browser flows, and live model responses remain unverified.


## October 14–15, 2026 — Retrieval, workflows, dashboard, and live verification

### Delivered
- V6 stored full-text search column and GIN index; owner-scoped keyword retrieval; deterministic reciprocal rank fusion with vector search. Both branches exclude unfinished documents. Keyword search works with embeddings disabled. Streaming RAG uses this shared retrieval service. The legacy `similarity` response field now contains a ranking weight, not a match percentage; the UI no longer displays it as a percentage.
- Bounded deterministic workflow router: retrieval, up to three research queries, summary of the first 20 document passages within the context budget, and fixed parameterized metadata inventory queries. Research continues after individual search failures but fails if all searches fail. Generation has an output cap; the HTTP workflow has a 60-second deadline and a bounded worker queue. Cancellation is cooperative, not a guarantee that an unresponsive external provider immediately releases a worker.
- Workflow sources, execution trace, missing-evidence abstention, explicit partial-summary disclosure, and citation-number validation. Citation validation checks structure only, not factual entailment. No persistent conversation memory or model-planned decomposition is claimed.
- Workspace dashboard with owner-scoped document counts, ready/processing/failed states, storage totals, passage search, source excerpts, and workflow controls.
- Isolated loopback-only Compose stack and PowerShell launchers; random local secrets stored only in ignored `.env.local`.
- Versioned fictional evaluation corpus, expected documents/reference answers, and repeatable retrieval evaluation runner.

### Defects found by actual execution
- Updated Testcontainers to 1.21.4 for compatibility with the current Docker engine.
- Original registration audit used a new transaction before its actor user was committed. Audit writes now run after successful actor transactions; anonymous failed-login audits use an independent transaction and survive rollback. Commit failures are caught outside the transaction boundary.
- `/auth/me` was accidentally covered by a public wildcard and crashed for anonymous callers. Only explicit authentication entry points remain public.
- The local launcher initially skipped environment keys containing digits, so it did not load `S3_SECRET_KEY`. Corrected the parser and verified uploads after restarting.
- Browser inspection found an encoding issue in new UI punctuation; normalized WorkspacePage.tsx to UTF-8 and reran frontend checks.

### Final checks
- **64 backend tests passed, 0 failures, 0 errors, 0 skipped**, including real PostgreSQL/pgvector and MinIO integration tests. All six migrations applied. Coverage includes registration/audits, role boundaries, document ownership, keyword/vector isolation, workspace and metadata scoping, deterministic fusion, workflow limits, citation structure, RAG sources-before-tokens, and no-evidence/model-failure behavior.
- Frontend TypeScript/production build and ESLint passed, including the final encoding correction.
- Live API smoke test registered a synthetic user, uploaded six fictional text files, observed all six complete processing, and searched the resulting passages. No live AI provider was used.
- Browser registration and the new dashboard rendered successfully. The subsequent browser file chooser check was interrupted by the in-app browser losing network connectivity while the computer was suspended; browser upload verification is incomplete. API and integration upload checks passed independently.
- October 15 follow-up: frontend returned HTTP 200 and backend health reported UP after resume.

### Measured local baseline
`evaluation/results.json` records the October 14 keyword-only run: six documents/six passages, eight queries (seven answerable, one unsupported), mean document Recall@5 **0.8571** (6/7), p50 **37.90 ms**, p95 **53.89 ms**. The unsupported query returned no result. The paraphrase challenge was missed. These are a single sequential run over a tiny synthetic corpus, not a production benchmark or real answer-quality measurement.

### Remaining limitations
- Real embeddings/generation, hybrid-vs-vector quality comparisons, and factual citation support still need a configured provider and a larger representative corpus. Unit tests use a fake provider.
- MinIO images could not be fetched from the original or alternate official registry during this session. Verification used the locally cached RELEASE.2025-09-07 image pinned by digest. Clean-machine portability requires an available trusted image or replacement service; details are in LOCAL_DEVELOPMENT.md.
- AWS deployment, distributed agent memory, rate limiting, feedback/admin operations, dependency security review, and production load testing remain future milestones. No cloud deployment or GitHub publication was performed.
- Docker startup troubleshooting preserved stale socket directories as backups under AppData/Local. No project containers or data volumes were deleted.

The local frontend is http://127.0.0.1:5174 and backend is http://127.0.0.1:8081. See LOCAL_DEVELOPMENT.md to restart or stop the development services.

## Product refresh — 2026-10-15

Replaced the engineering console as the main experience with Folio: a warm, responsive document workspace with focused navigation, onboarding using a fictional sample, a document library, scoped passage search, original-source panels, upload progress, processing status, previews, retry, and removal confirmation. Draft questions survive sign-in; modal keyboard navigation and reduced-motion styling are supported.

Reproduced the screenshot's misleading Authentication required error: a stream-only Accept header caused the disabled-generation error response to be masked by an unauthenticated error dispatch. Corrected response content type, allowed async/error dispatch while keeping request authentication, and made the chat client accept both streaming and JSON responses. Unexpected stream closure now reports an actionable failure.

Validation: 66 backend tests passed, including disabled-generation HTTP negotiation and real HTTP streaming with a mocked model. Frontend production build and lint passed. Browser-verified sample upload, automatic processing, scoped keyword retrieval, source opening, and unavailable-AI guidance. Inspected 390px responsive layout with no horizontal overflow. Saved artifacts/folio-product.jpg. Restarted local backend with the tested changes.

Limits: no live AI provider is configured; generated-answer quality has not been verified with a real model. Current local launchers keep generation and embeddings disabled. Search works without them. The browser is left signed into an isolated synthetic smoke-test account; user documents were not changed. Product refresh is local, not published or pushed.

## Saved conversations and local AI — 2026-10-15

Added owner-scoped PostgreSQL conversation snapshots (V7), title editing, explicit save/update, paginated history, unsaved-change warnings, copy-with-sources, and Markdown export. Saved source excerpts remain snapshots; new questions still search independently rather than receiving chat memory. Browser verification confirmed save, full reload, reopen, source opening, and actual export download. Fixed JSON property ordering causing reopened saved conversations to look dirty.

Installed official Ollama locally using a loopback-only Compose service and downloaded qwen2.5:3b. Added Enable-LocalAI.ps1 and independent generation endpoint configuration. The backend launcher now honors configured generation settings and starts the local-AI profile. Embeddings remain disabled; no hosted model is used.

Live testing caught strict keyword conjunction missing natural-language questions. Added a parsed-term OR fallback only when strict retrieval finds nothing, preserving ownership, ready-state and document filters. Integration checks for natural questions, missing evidence and cross-owner access passed (3 tests). Saved-conversation auth/persistence checks passed (7 tests). Agent tests passed (11 tests), including actionable rejection of uncited output. The preceding full backend run passed 66 tests before the additional regressions. Frontend build and lint passed.

A first real-model summary lacked citations and was correctly rejected, but exposed a generic error. Strengthened the generation instructions with concrete citation examples and added a 422 response explaining how to retry or use original passages. Citation validation remains a structural check, not proof of factual accuracy. Live smoke results are saved separately in artifacts/local-ai-smoke.json when verification completes.

Final live verification passed with the real local model: the vacation question returned 20 days with [1]; an unrelated spacecraft question returned no supporting evidence without generation; summarization returned four supported bullets with [1]. Recorded final timings: answer 50.58s, no-evidence response 0.28s, summary 13.23s. Earlier warm answer took 12.27s, so CPU timing is variable. Results: artifacts/local-ai-smoke.json. Browser export verified in Downloads/folio-conversation.md; screenshot: artifacts/folio-local-ai.jpg. Local backend and model remain running. Browser remains in the synthetic smoke-test account. No GitHub push or deployment was performed.

## Collections and clearer waiting states — 2026-10-16

Added account-private collections (V8), creation/rename/removal, document movement and upload placement, filtered libraries, and collection scope through keyword/vector retrieval, streamed answers, agent workflows, and saved conversations. Removing a collection keeps its documents. Existing saved conversations remain compatible. Added elapsed waiting status and a clear message for slower local inference, retaining Stop response.

Validation: full backend suite passed 71 tests with zero failures/errors/skips. Coverage includes ownership, filtered results before vector ranking, document preservation after collection removal, and existing chat/auth flows. Frontend build and lint passed. Browser verified creating Team handbook in the synthetic account, moving the sample handbook, scoped search, saving and reopening the collection-scoped conversation after a full reload. Improved empty-collection guidance and accurate collection document counts after visual testing. User documents were not modified.


## GitHub project snapshot — 2026-10-16

Initialized an independent Git repository inside the Folio project, excluding the parent home-directory repository. Created private GitHub repository Sriram-Mullapudi/folio-knowledge-platform. Prepared a source snapshot with code, tests, migrations, documentation, local setup scripts, and synthetic verification artifacts. Configured secrets were checked against candidate files and excluded; runtime databases, uploads, model weights, logs, dependencies and build outputs remain local. AGENTS.md records the user-authorized preference to commit and push completed, tested future project work. This is not a background backup schedule. Existing validation: 71 backend tests passed and frontend build/lint passed.

## Answer evaluation and demo readiness — 2026-10-16

Added six fictional answer fixtures and a local-only, authenticated upload/retrieval/generation evaluation harness. The real qwen2.5:3b model passed all eight automated cases, covering direct facts, archived/current policies, cross-document comparison, safe incident steps, missing information, no evidence, and one malicious document-instruction fixture. Manually reviewed saved answers against the retrieved excerpts. These small heuristic checks are not a production accuracy or security claim.

Compared four identical retrieved contexts with warmed qwen2.5:3b and qwen2.5:1.5b. The smaller model omitted citations in two cases, so the existing 3b default remains. Saved both successes and failures. Added a five-document fictional demo and walkthrough.

Added loopback-only local-model preparation after backend build, with non-blocking failure and a local opt-out. Increased Ollama idle retention from five to 15 minutes. Applied the Compose change and successfully prepared 3b in 49.0 seconds. This shifts loading into startup and retains memory longer; it does not remove CPU generation costs or cold delays after idle expiry. PowerShell parser checks and Python compilation passed. No Java or frontend source changed; their prior 71-test/build/lint results were not rerun.

Post-preparation full-app verification passed 8/8 again. First answer: 10.97s total, 7.13s to first text, compared with 74.35s before preparation. Remaining generated answers took 3.55–23.44s; timings remain variable. Results saved in evaluation/answer-results-warm.json.

## Follow-up questions — 2026-10-17

Ask a question now sends up to three completed, non-error turns from the same document and collection scope. Previous questions help retrieval resolve follow-ups; prior answers supply untrusted reference context only. Fresh owner-scoped document retrieval supplies evidence and citation numbers every time. No-evidence answers still skip generation. Request validation bounds history size and nested fields. Added an opt-out switch; New question clears history. Saved turns retain scope and completion metadata, while older snapshots remain readable and are excluded from follow-up context. Search, research and summaries remain independent.

Validation: 7 RAG/HTTP tests passed, then 14 auth/persistence/RAG tests passed after metadata changes (overlapping suites, not 21 unique tests). Frontend build and lint passed; the initial sandbox build hit a filesystem permission error and the approved retry succeeded. Browser inspection confirmed the context control is visible; no user documents were modified. Restarted the local backend with the new build.

Live local-model checks passed: initial Atlas owner, launch follow-up after saved-conversation reload, correction of a fabricated 2099 date in prior history, and abstention on missing launch-location evidence. Timings: 15.92s, 5.24s, 4.94s, 4.81s. The missing-location response repeated the unsupported phrase spacecraft Atlas while declining to provide a location; this remains a wording/false-premise limitation. Four short synthetic cases are not proof of general conversation accuracy. Results: evaluation/followup-results.json.
