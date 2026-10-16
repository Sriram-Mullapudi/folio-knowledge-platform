-- Runs once, as the superuser, on first initialization of the data volume.
-- Ensures the pgvector extension exists even before Flyway runs. Flyway also
-- declares it (idempotently) so the application is self-contained on managed
-- databases where this init hook does not run.
CREATE EXTENSION IF NOT EXISTS vector;
