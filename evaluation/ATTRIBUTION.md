# Employer attribution verification — 17 October 2026

The reported problem was attributing general resume skills to a specific employer. The final change narrows referential follow-up retrieval, selects literal role excerpts for recognized employer technology questions, and omits old generated answers from those prompts. Current owner/document/collection filters still apply.

One fictional resume contains two employers with distinct technologies and a separate general-skills section. Three questions were run against the actual local model before and after changes. The baseline already answered this fixture correctly, so this is not evidence of a measured general accuracy gain. An intermediate revision failed the employer-switch check by copying skills from a mixed role/general-skills passage; that failure is retained in attribution-intermediate.json.

The final run passed all six heuristic checks across three answers:
- Cedar Health work: scheduling portal, cited.
- Follow-up technologies: Java, Spring Boot, React, PostgreSQL, cited; no unrelated skills.
- Explicit switch to Birch Payments: Go, Redis, Kafka, cited; no unrelated skills.

For the follow-up, the evidence narrowed from two chunks (including the other role and general skills) to one literal Cedar Health excerpt. For the switch, the excerpt ends before General skills. Manual review confirms the listed technologies are supported in these short fixtures. Raw answers and sources are in attribution-before.json and attribution-results.json.

Thirteen final unit/service regressions passed; the HTTP streaming test passed in the earlier 11-test run. No frontend source changed. The final build was restarted locally. Initial packaging encountered a Windows lock on the running jar and succeeded after stopping the service.

Limits: this is an English, heading-sensitive heuristic, not section-aware indexing or a factuality proof. Only recognized employer names and technology wording trigger excerpt filtering. Relevant continuation chunks without the employer name may be omitted. Chunk boundaries and document formatting can affect results. The model does not consistently obey sentence-level citation instructions. General semantic retrieval and broader multi-document/long-PDF evaluation remain open work. User resume contents and screenshots are excluded from the repository.
