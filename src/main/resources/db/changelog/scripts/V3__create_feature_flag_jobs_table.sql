-- Create feature flag jobs table for tracking background processing
CREATE TABLE feature_flag_jobs (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    feature_flag_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_organizations INTEGER,
    processed_organizations INTEGER DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    parent_job_id BIGINT,
    
    CONSTRAINT fk_feature_flag_jobs_organization 
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_feature_flag_jobs_parent 
        FOREIGN KEY (parent_job_id) REFERENCES feature_flag_jobs(id)
);

-- Create indexes for better query performance
CREATE INDEX idx_feature_flag_jobs_organization_id ON feature_flag_jobs(organization_id);
CREATE INDEX idx_feature_flag_jobs_status ON feature_flag_jobs(status);
CREATE INDEX idx_feature_flag_jobs_created_at ON feature_flag_jobs(created_at);
CREATE INDEX idx_feature_flag_jobs_parent_job_id ON feature_flag_jobs(parent_job_id);
