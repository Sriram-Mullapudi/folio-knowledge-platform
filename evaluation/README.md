# Retrieval and answer evaluation

Run `python evaluation/run.py --base-url http://127.0.0.1:8080` from the repository root against an isolated local instance. Each run creates a synthetic account and six fictional policy documents. No login credentials are printed or persisted. Do not point this at your everyday workspace; retain or remove the isolated development volume after review.

The corpus has eight labelled questions including an exact error code, a paraphrase challenge, and an unsupported question. The report measures document-level Recall@5 over the top five chunks, no-result behavior, and p50/p95 latency for one small sequential run. It records the server's configured retrieval mode. These are development diagnostics, not production benchmarks.

For a no-cost baseline, leave embeddings and generation disabled. To compare hybrid retrieval, use a separately configured embedding-enabled instance; embedding calls may incur provider charges. Keep corpus, hardware, and model version constant and record warm-up and concurrency settings before claiming performance gains.

For real local answer evaluation, run `python evaluation/answer_eval.py` (defaults to port 8081). It creates its own synthetic account, uploads six fictional documents, and checks eight questions for facts, expected citation references, missing-information responses, and one document-injection case. Failed checks return a nonzero exit code. Review the answers manually: these heuristics cannot prove factual entailment. Synthetic accounts/documents remain in the development database; credentials are never saved.

With both qwen2.5:3b and qwen2.5:1.5b installed in local Ollama, run `python evaluation/model_compare.py` after the answer evaluation. It reuses the retrieved evidence for four selected cases, warms each model, and records model-stage timings and checks without changing the app configuration. Comparison failures are recorded as findings rather than a failing process exit. Use `--output` to preserve previous result files.

See [measured results and limitations](ANSWER_QUALITY.md). This small fictional set and its demo questions are not an independent production benchmark. Unit tests still use a fake provider and validate control flow and citation structure, not real-model accuracy.

Full-text indexing follows the PostgreSQL 16 stored generated tsvector and GIN pattern: https://www.postgresql.org/docs/16/textsearch-tables.html
