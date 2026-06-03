
-- =========================================================================
-- 1. GOVERNANCE SERVICE DATABASE SCHEMA (governance_db)
-- =========================================================================

-- Target Database: governance_db
-- Description: Stores governance policies and manages their current lifecycle state.

CREATE TABLE policies (

    id BIGSERIAL PRIMARY KEY,
    
    title VARCHAR(255) NOT NULL,
    
    description TEXT,
    
    status VARCHAR(50) NOT NULL,
    
    created_by VARCHAR(100) NOT NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- =========================================================================
-- 2. AUDIT SERVICE DATABASE SCHEMA (audit_db)
-- =========================================================================

-- Target Database: audit_db
-- Description: Stores immutable logs of all policy status changes for auditing.

CREATE TABLE audit_logs (
    
    id BIGSERIAL PRIMARY KEY,
    
    event_type VARCHAR(100) NOT NULL,
    
    policy_id BIGINT NOT NULL,
    
    actor VARCHAR(100) NOT NULL,
    
    timestamp TIMESTAMP NOT NULL
);
