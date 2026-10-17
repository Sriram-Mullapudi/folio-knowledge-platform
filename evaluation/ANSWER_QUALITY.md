# Local answer quality — 16 October 2026

Six fictional documents; eight questions through the real authenticated Folio upload, retrieval and streaming-answer APIs. Local qwen2.5:3b, keyword retrieval, CPU inference, one sequential run. All eight automated checks passed. Manual review of the saved answers against their excerpts found the requested facts supported, both policy versions correctly distinguished, and no invented answer for missing information.

| Case | Check | First text (s) | Complete (s) |
| --- | --- | ---: | ---: |
| leave-facts | Pass | 71.5 | 74.35 |
| current-policy | Pass | 5.99 | 7.95 |
| policy-change | Pass | 11.86 | 18.12 |
| project-details | Pass | 4.03 | 6.8 |
| incident-safety | Pass | 3.53 | 6.05 |
| missing-but-related | Pass | 1.09 | 3.25 |
| no-evidence | Pass | 0.02 | 0.03 |
| document-injection | Pass | 3.46 | 6.19 |

The first answer took 74.35s; subsequent generated answers had a 6.50s median. Retrieval took 0.02–0.18s. The unsupported question returned immediately without model generation. Cold model loading is a likely contributor to the first delay, but this run did not isolate loading from prompt processing.

## Smaller-model experiment

Four selected questions reused identical retrieved evidence and prompts. Each model received a short warmup before measurement. This isolates the model stage and excludes retrieval/application overhead; it is not comparable directly to the full-app timings above. Models ran in fixed order (3b, then 1.5b), with possible prompt-cache and order effects.

| Model | Cases passing | Median total (s) | Observation |
| --- | ---: | ---: | --- |
| qwen2.5:3b | 4/4 | 3.02 | All four answers included the required evidence references. |
| qwen2.5:1.5b | 2/4 | 3.00 | Omitted citations for leave and vendor-hours answers; facts were correct. |

Keep qwen2.5:3b as the default. The smaller model did not meet the citation requirement in this sample, and the timing difference does not justify switching.

## Product change

The launcher now preloads local AI after building the backend. A failed warmup warns and still allows Folio to start. The model stays resident for 15 minutes of inactivity instead of five, reducing reloads between questions at the cost of retaining memory longer. Warmup moves loading into startup; it does not make CPU generation intrinsically faster. After idle expiry a cold delay can recur. Run `scripts/Warm-LocalAI.ps1` before a demo, or set `LOCAL_AI_WARMUP=false` in the ignored local configuration to skip startup preparation. The helper only calls the exact loopback Ollama endpoint.

Implementation follows [Ollama’s documented preload and keep-alive APIs](https://docs.ollama.com/faq#how-can-i-preload-a-model-into-ollama-to-get-faster-response-times).

## Limits and reproduction

These are short, authored text documents, not a representative user corpus. Phrase checks and citation-number checks do not prove factual entailment. The single injection case is not a security audit. This does not measure large PDFs, OCR, paraphrase recall, concurrency, repeated-run variance, or multi-turn reasoning. Earlier keyword retrieval testing missed a paraphrase challenge.

Raw results: `answer-results.json` and `model-comparison.json`. Run `python evaluation/answer_eval.py` against the local app; it creates a separate synthetic account without persisting credentials. It exits nonzero on a failed case. After installing both local models, run `python evaluation/model_compare.py`. The comparison records failures as findings and does not change the app model. Neither harness deletes user data. Synthetic records remain in the development database.

See `demo/WALKTHROUGH.md` for the accompanying fictional demo.

## Verification after startup preparation

The warmup completed in 49.0s. A second full-app run passed all eight cases again; the first answer completed in 10.97s (first text 7.13s), versus 74.35s before preparation. This is one observed before/after run, not a guaranteed speedup; later answers ranged from 3.55s to 23.44s and CPU timings vary. Raw results are in `answer-results-warm.json`. The no-evidence response took 0.06s.
