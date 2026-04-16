-- 合同主数据与条款块（业务权威存储；向量库为派生检索索引）
CREATE TABLE IF NOT EXISTS contracts (
    id VARCHAR(64) PRIMARY KEY,
    contract_type VARCHAR(32) NOT NULL,
    party_a_name VARCHAR(255) NOT NULL,
    party_b_name VARCHAR(255) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    amount_ex_tax NUMERIC(19, 2) NOT NULL,
    tax_rate_pct NUMERIC(10, 4) NOT NULL,
    amount_inc_tax NUMERIC(19, 2) NOT NULL,
    sign_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    end_date DATE NOT NULL,
    performance_site TEXT NOT NULL DEFAULT '',
    payment_terms_summary TEXT NOT NULL DEFAULT '',
    business_owner_dept VARCHAR(255) NOT NULL DEFAULT '',
    risk_tier VARCHAR(16) NOT NULL,
    vector_doc_id VARCHAR(128),
    notes TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS clause_chunks (
    contract_id VARCHAR(64) NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    chunk_id VARCHAR(64) NOT NULL,
    clause_code VARCHAR(64) NOT NULL DEFAULT '',
    clause_title VARCHAR(512) NOT NULL DEFAULT '',
    clause_category VARCHAR(128) NOT NULL DEFAULT '',
    party_focus VARCHAR(32) NOT NULL DEFAULT '',
    risk_flag VARCHAR(16) NOT NULL DEFAULT 'LOW',
    source_section VARCHAR(128) NOT NULL DEFAULT '',
    text_for_embedding TEXT NOT NULL,
    related_amount_field VARCHAR(64) NOT NULL DEFAULT '',
    review_priority VARCHAR(32) NOT NULL DEFAULT '',
    PRIMARY KEY (contract_id, chunk_id)
);

CREATE INDEX IF NOT EXISTS idx_clause_chunks_contract_id ON clause_chunks (contract_id);

CREATE TABLE IF NOT EXISTS approval_records (
    contract_id VARCHAR(64) NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    approval_record_id VARCHAR(64) NOT NULL,
    step_no INTEGER NOT NULL,
    approver_role VARCHAR(255) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    decision_time TIMESTAMP WITH TIME ZONE,
    comment_summary TEXT NOT NULL DEFAULT '',
    linked_policy_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    linked_clause_chunk_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    risk_items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    vector_doc_id VARCHAR(128),
    PRIMARY KEY (contract_id, approval_record_id)
);

CREATE INDEX IF NOT EXISTS idx_approval_records_contract_id ON approval_records (contract_id);
CREATE INDEX IF NOT EXISTS idx_approval_records_contract_step ON approval_records (contract_id, step_no);
