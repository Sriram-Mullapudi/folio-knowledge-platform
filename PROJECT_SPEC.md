# Enterprise Knowledge Platform - Project specification

## Product
An agentic RAG platform for uploading documents and asking questions in plain English. Answers stream with numbered citations to source passages. A bounded agent router chooses retrieval, safe metadata queries, multi-step research, or summarization.

The problem: keyword search misses paraphrases, while ungrounded LLM answers cannot be verified. The platform combines semantic and lexical retrieval, limits generation to retrieved evidence, and makes source passages inspectable.

## Source and working directory
The original idea is preserved verbatim in `PROJECT_IDEA_ORIGINAL.txt`. This working copy was created from `C:/Users/srira/Downloads/enterprise-knowledge-platform (3)` on October 14, 2026. The original folder is unchanged. The owner confirmed that no newer version is available.

Historical statements in the brief are reported background, not proof of the current checkout. This document separates requirements, code found, and executed verification. Do not use projected performance, deployment cost, or claimed test counts as measured results.

## Target stack
- React 18, TypeScript, Vite, Tailwind.
- Java 17, Spring Boot (existing version 3.3.5; dependency review required before public deployment).
- PostgreSQL 16 with pgvector, Redis, MinIO/S3, nginx.
- Docker Compose locally; Terraform and ECS Fargate for an eventual AWS deployment.
- OpenAI embeddings with 1536 dimensions and streamed generation behind provider interfaces.

## Original source inventory (before this development pass)
- Found: JWT/security configuration, USER/ADMIN roles, refresh tokens, audit service, document upload/download/delete, hash deduplication, text extraction, chunking, embedding client, vector repository, context assembler, streaming RAG classes, and React frontend.
- Found five migrations (V1-V5), not the seven described in the brief.
- SearchService currently requires embeddings and performs vector-only retrieval. Keyword fallback and RRF are not implemented here.
- AdminController currently exposes a user list, not a complete administration dashboard.
- The first build confirmed existing unit and Docker-backed integration test sources. These cover auth, upload, vectors, chunking, context assembly, and the chat client; additional ownership cases are needed.
- Agent routing, full feedback/metrics tooling, deployment infrastructure, and real model behavior require implementation or verification; do not assume they are complete.
- Existing ownership is per user. Organization-level tenant membership is a separate design decision, not an already verified feature.

## End-to-end flows
### Ingestion
Browser -> nginx -> authenticated upload -> validate file -> owner-scoped SHA-256 deduplication -> object storage -> UPLOADED -> durable asynchronous extraction/chunking/embedding -> COMPLETED, or FAILED with an actionable error.
Retries must not duplicate chunks. Processing claims must prevent simultaneous workers from processing the same document. Document deletion, retries, and object-store failures need explicit consistency behavior.

### Question answering
Authenticate -> enforce ownership -> embed question when configured -> cosine and full-text search -> Reciprocal Rank Fusion -> budgeted context -> stream answer and source metadata over SSE. If embeddings are disabled, use keyword retrieval. If generation is disabled, explain that limitation without fabricating an answer.
Citations must reference retrieved, authorized passages. A model must be able to abstain when evidence is insufficient. Documents are untrusted data, never instructions granting tool or data access.

### Agent routes
1. Retrieval: targeted document question answering.
2. SQL metadata: select only allowlisted parameterized queries, scoped to the caller. Never execute model-generated SQL. This reduces SQL-injection risk; it does not replace authorization and validation.
3. Research: bounded decomposition, retrieve per sub-question, synthesize from evidence.
4. Summarization: summarize authorized documents with citations and chunking for long files.
Bound total steps, tokens, execution time, and memory. Record useful traces without secrets or other users' content. Isolate step failures; store conversation state in Redis before multi-node deployment.

## Delivery milestones and acceptance criteria
1. **Baseline and security tests:** repeatable build; anonymous access denied; USER blocked from administration; owner-only metadata, chunks, download, delete, reprocess; no storage access on denied operations. Distinguish mocked service tests from real database isolation tests.
2. **Reliable ingestion:** validate MIME/content and upload limits; owner-scoped dedupe; retry, concurrent worker, crash recovery, and object-storage failure tests with actual services.
3. **Hybrid retrieval:** tsvector/GIN migration, cosine/HNSW retrieval, deterministic RRF, caller scoping in both branches, keyword-only operation without a model key.
4. **Grounded streaming RAG:** bounded context, numbered citations, no-evidence response, SSE disconnect/error handling, prompt-injection evaluation and citation checks. Fake-provider tests first, then live-provider verification.
5. **Agent layer:** four strategies, constrained metadata intents, bounded research loops, owner-scoped memory and full traces. Test routing and failure behavior deterministically.
6. **Operations:** feedback, admin statistics/documents/audit views, owner-aware cache keys, Prometheus metrics, per-user LLM rate limits and cost budgets.
7. **Evaluation:** versioned corpus and labelled questions; measure Recall@5/10, citation accuracy, abstention behavior, and p50/p95 latency for keyword/vector/hybrid configurations. Record hardware, index size, concurrency, sample count, and model versions.
8. **AWS:** review dependencies, configure TLS, private services and secrets, OIDC deployment, backup/restore drills and measured RTO/RPO. Estimate cost from actual configuration; the brief's ~$210/month is an unverified estimate. Run deployment only with an explicitly selected account/region and spending scope.

## Implemented upgrade pass
- Added V6 full-text index, shared hybrid retrieval and keyword fallback.
- Added deterministic workflow routing, up to three research searches, partial document summaries, parameterized inventory queries, trace steps, and citation-number validation. These are bounded workflows, not a model-planned autonomous agent or persistent conversational memory.
- Added an owner-scoped workspace dashboard, passage search, and workflow UI.
- Added a labelled synthetic evaluation corpus and runner.
- Fixed registration audit ordering and anonymous profile access, discovered by full integration tests.
- See WORK_LOG.md for final test results and external limitations.

## Immediate work
- Preserve requirements and establish the actual source baseline.
- Add document ownership and HTTP role-boundary tests without paid model calls.
- Fix defects demonstrated by tests, with regression coverage.
- Maintain `WORK_LOG.md` with commands, results, and limitations.
- Next functional milestone: hybrid search and keyword fallback, followed by deterministic agent routing.

## Definition of done
A feature is complete only when implemented, tested at the appropriate boundary, documented, and demonstrable. Live OpenAI behavior, cloud deployments, scale claims, and production readiness remain unverified until actually exercised. Do not commit credentials, private corpora, or generated secrets.
