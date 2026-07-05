-- Optional reset for a clean local evaluation database.
-- Run this only against a disposable dev/test database.

TRUNCATE TABLE approval_records CASCADE;
TRUNCATE TABLE clause_chunks CASCADE;
TRUNCATE TABLE contracts CASCADE;
TRUNCATE TABLE policy_knowledge CASCADE;

-- Spring AI pgvector table. If your table name differs, adjust this line.
TRUNCATE TABLE vector_store CASCADE;
