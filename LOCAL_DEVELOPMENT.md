# Local development

This isolated stack uses its own Compose project (`ekp-development`) and volumes. It does not reuse the old `ekp-*` containers. Services bind to the local computer only.

## Start
Install Java 17, Node.js, and Docker Desktop, and start Docker's Linux engine.

From this directory, run in two PowerShell terminals:

```powershell
.\scripts\Run-Backend.ps1
```

```powershell
.\scripts\Run-Frontend.ps1
```

Open http://127.0.0.1:5174 and register a local account. The backend is http://127.0.0.1:8081. PostgreSQL uses port 55432, Redis 56379, and MinIO 59000/59001. The backend script creates random local credentials in the ignored `.env.local` file when absent. Do not share that file.

The scripts deliberately disable embeddings and generation for a free local baseline. Keyword search, uploads, processing, document inventory, and workspace statistics work in this mode. AI research, summaries, and answers require a configured generation provider. The source supports hybrid retrieval when embeddings are enabled, but live-provider verification is a separate step.

## Verify

```powershell
cd backend
.\mvnw.cmd -B test
```

Testcontainers 1.21.4 supports the installed Docker engine. Tests create their own disposable PostgreSQL and MinIO containers; all eight Flyway migrations are exercised. Frontend checks: `npm.cmd run build` and `npm.cmd run lint` from `frontend`.

```powershell
python evaluation/run.py --base-url http://127.0.0.1:8081
```

See `evaluation/README.md` for interpretation and limitations.

## Image availability

The old MinIO image could not be pulled from Docker Hub or its official alternate registry during verification. This computer has MinIO RELEASE.2025-09-07T16-13-09Z cached; the local stack and tests pin its digest. On a clean machine, supply an available trusted MinIO image using `MINIO_IMAGE` for Compose and `-Dekp.test.minio-image=<image>` for Maven. This external distribution issue remains a portability limitation; the cached image is not a claim of current security support. Plan an actively maintained S3-compatible service before public deployment.

## Stop

Stop each PowerShell process with Ctrl+C, then run:

```powershell
docker compose --env-file .env.local -f infra/compose.local.yml stop
```

Volumes retain your documents. No automatic volume deletion is performed.

## Local AI (no paid API key)

Run `scripts/Enable-LocalAI.ps1` once, then restart with `scripts/Run-Backend.ps1`. The setup downloads Ollama and `qwen2.5:3b`, stores model files in the `ekp-development_dev-ollama` Docker volume, and configures generation in the ignored `.env.local`. The local endpoint is bound to `127.0.0.1:11434`. Downloads require internet; local inference does not send document content to a hosted model. The compatibility key `ollama-local` is a non-secret placeholder ignored by Ollama.

The launcher starts the local-AI Compose profile whenever this local endpoint is enabled. Generation has its own `GENERATION_BASE_URL` and `GENERATION_API_KEY`, independent of embedding settings. Embeddings remain disabled; retrieval uses keyword search. CPU inference may be slower than hosted generation. A larger model can be selected with the setup script's `-Model` option if hardware permits.

To return to passage-only search, set `GENERATION_ENABLED=false` in `.env.local` and restart the backend. To stop the model service without removing downloaded models, run `docker compose --env-file .env.local -f infra/compose.local.yml --profile local-ai stop ollama` from the project folder.

The backend launcher prepares the local model after its build, before starting the app. Preparation may take about a minute on this computer; failure produces a warning and does not block startup. Ollama keeps the model in memory for 15 minutes after activity. This reduces reloads but holds RAM longer. After that idle period the next answer can be slow again. Run `scripts/Warm-LocalAI.ps1` before a demo to prepare it manually. Set `LOCAL_AI_WARMUP=false` in `.env.local` to skip preparation. This helper only supports the exact local endpoint above and never calls a hosted provider.

See [measured answer results](evaluation/ANSWER_QUALITY.md) and the [fictional demo walkthrough](demo/WALKTHROUGH.md). The smaller qwen2.5:1.5b model omitted required citations in two of four comparison cases, so qwen2.5:3b remains the default.

## Saved conversations

Use **Save conversation** after a search or answer. Saved work is stored in PostgreSQL under the signed-in account, including source excerpt snapshots. Name a conversation, return through **Saved conversations**, or export it as Markdown. Saving an open conversation updates it; unsaved changes trigger a warning before replacement or browser reload. In Ask a question mode, up to three recent completed turns from the same document and collection selection provide follow-up context. Every answer searches current documents again; previous answers are not evidence. Turn off Use recent conversation for an independent question, or choose New question to start fresh. Failed/stopped turns and older saved turns without completion/scope metadata are excluded. Search, research and summary modes remain independent. Excerpts saved earlier may refer to documents that have since changed or been removed.

## Collections

Use the Collection selector to create a project collection or focus the library and questions on an existing one. Each document belongs to at most one collection. Move it using the selector on its library row; uploading while a collection is selected places the new document there. Rename collections without moving files. Removing a collection returns its documents to the ungrouped library; it does not delete them. Saved conversations remember their collection scope. If a saved collection has since been removed, choose an available scope before continuing.

## Follow-up verification

Run `python evaluation/followup_eval.py` against the local running app. This uses fictional documents and a new synthetic account, checks a saved conversation round trip, resolves an Atlas follow-up, rejects an incorrect date in history, and checks missing-information behavior. History is capped at three turns (500 characters per question, 1,000 per answer). Retrieval uses previous question terms plus the new question; there is no separate question-rewriting model call. Long conversations, unrelated topic switches and references that appear only in earlier answers can still require a more explicit question.

## Attribution precision

Referential follow-ups (for example, Which technologies did he use there?) now intersect their retrieved chunks with those retrieved for the latest explicit question in the supplied history. If that intersection is empty, only the earlier topic passages are used. Both searches retain the current owner, document and collection filters. Clearly named topic switches such as at Birch Payments use the new question alone. Generation instructions prohibit attributing general skills or another role to a named employer and require citations next to claims.

This is a conservative English heuristic, not an entity resolver or an attribution guarantee. Broad matches in the original question can still contain mixed sections. Relevant details outside the topic passages may be omitted; ask an explicit question to broaden the search. Section-aware indexing and stronger semantic retrieval remain future work.

Employer technology questions also use conservative excerpt filtering when an employer can be recognized after at/for in the current question or referential question history. Only chunks containing that employer remain; excerpts begin at its name and stop at recognized skills/education/summary headings or a following dated pipe-formatted role heading. Earlier generated answers are omitted for technology questions so an old incorrect skill list cannot seed the next answer. Other layouts, lowercase employer names, or technology synonyms may not trigger this heuristic. It can omit valid continuation paragraphs, and it does not guarantee completeness or correctness.

## Section-aware search passages

New uploads split at recognized Markdown headings, standard resume section labels, short dated employer headings, and explicit Project:/Policy: labels. Continuation passages repeat the original heading so keyword search can still associate a late detail with its section. Markdown parent headings are retained within the character budget. Overlap stays inside a section; unstructured text uses the previous splitter. Repeated headings are context copied from the document, not newly generated facts or exact adjacent text from the original page.

For existing files, use **My documents → Update search**. This reprocesses the stored original and replaces its search passages; it does not upload another file or modify the original. Saved conversations retain their earlier excerpt snapshots and passage numbers may change. Search is temporarily unavailable for that document while it is processing. Existing files are not silently reprocessed. If the button is missing in an older open tab, save your conversation before refreshing the page.

Recognition is heuristic and English-oriented. PDF text must already extract into meaningful lines; this does not add OCR, table understanding, PDF page locations, or semantic embeddings. Unusual headings can be missed, and short date-bearing lines may be mistaken for headings. Heading repetition improves the tested continuation searches, not all paraphrases or model accuracy.

Run `python evaluation/section_eval.py` for synthetic long-section retrieval, Markdown hierarchy, owner isolation, and reprocessing checks.
# Local semantic search

Run `scripts/Enable-LocalSearch.ps1`, then restart `scripts/Run-Backend.ps1`.
This downloads Ollama's `nomic-embed-text:v1.5` model and configures embeddings
independently of answer generation. No API key is needed; document text stays
on this computer. Model download needs internet once. The setup does not
overwrite generation credentials. Existing passages are indexed on startup;
new uploads are indexed after processing. Original uploads are unchanged.

Queries combine keyword and semantic rankings. If the embedding request fails,
keyword search remains available (after the configured request timeout). Each
vector query retains owner/document/collection filters and selects only the
configured model. Changing models schedules old vectors for replacement at
startup. Keyword search works while indexing is incomplete.

The local model produces 768 values. They are validated and zero-padded to the
existing 1536-value storage column; this preserves cosine similarity without a
destructive schema migration. Query/document task prefixes are applied only to
the supported local Nomic model. Ollama can keep two models loaded within the
existing 5 GB memory limit; limited hardware may still reload models and be slow.

Run `evaluation/semantic_eval.py` against the local app for three fictional
paraphrase rankings, one cited answer, and cross-account isolation. This is a small retrieval
check, not a comprehensive answer correctness benchmark.
