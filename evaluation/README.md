# Retrieval and answer evaluation

Run `python evaluation/run.py --base-url http://127.0.0.1:8080` from the repository root against an isolated local instance. Each run creates a synthetic account and six fictional policy documents. No login credentials are printed or persisted. Do not point this at your everyday workspace; retain or remove the isolated development volume after review.

The corpus has eight labelled questions including an exact error code, a paraphrase challenge, and an unsupported question. The report measures document-level Recall@5 over the top five chunks, no-result behavior, and p50/p95 latency for one small sequential run. It records the server's configured retrieval mode. These are development diagnostics, not production benchmarks.

For a no-cost baseline, leave embeddings and generation disabled. To compare hybrid retrieval, use a separately configured embedding-enabled instance; embedding calls may incur provider charges. Keep corpus, hardware, and model version constant and record warm-up and concurrency settings before claiming performance gains.

Answer quality remains a separate evaluation: use the reference answers to manually assess factual support, citation accuracy, completeness, and abstention after a real generation provider is configured. Unit tests use a fake provider and validate control flow and citation structure, not real-model accuracy.

Full-text indexing follows the PostgreSQL 16 stored generated tsvector and GIN pattern: https://www.postgresql.org/docs/16/textsearch-tables.html
