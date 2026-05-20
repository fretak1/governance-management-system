-- Database Schema Reference Script (Day 1 - Step 3)
-- Note: Under Option 1, Spring Data JPA / Hibernate will automatically generate these tables.
-- This file serves as the official documentation and reference for the database design.

-- =========================================================================
-- 1. GOVERNANCE SERVICE DATABASE SCHEMA (governance_db)
-- =========================================================================

-- Target Database: governance_db
-- Description: Stores governance policies and manages their current lifecycle state.

CREATE TABLE policies (
    -- Unique identifier, auto-incrementing 64-bit integer
    id BIGSERIAL PRIMARY KEY,
    
    -- Title of the governance policy
    title VARCHAR(255) NOT NULL,
    
    -- Detailed description of the policy constraints/guidelines
    description TEXT,
    
    -- Current lifecycle status of the policy.
    -- Allowed values: 'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'
    status VARCHAR(50) NOT NULL,
    
    -- Username or ID of the user who created this policy
    created_by VARCHAR(100) NOT NULL,
    
    -- Date and time when the policy was first created
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- =========================================================================
-- 2. AUDIT SERVICE DATABASE SCHEMA (audit_db)
-- =========================================================================

-- Target Database: audit_db
-- Description: Stores immutable logs of all policy status changes for auditing.

CREATE TABLE audit_logs (
    -- Unique identifier, auto-incrementing 64-bit integer
    id BIGSERIAL PRIMARY KEY,
    
    -- Type of event that occurred.
    -- Allowed values: 'policy-created', 'policy-submitted', 'policy-approved', 'policy-rejected'
    event_type VARCHAR(100) NOT NULL,
    
    -- Reference ID matching the Policy ID in the governance service
    policy_id BIGINT NOT NULL,
    
    -- Username of the actor who performed the action (e.g., 'admin', 'manager')
    actor VARCHAR(100) NOT NULL,
    
    -- Timestamp when the action took place
    timestamp TIMESTAMP NOT NULL
);
