CREATE TABLE dataset_doi (
    id SERIAL PRIMARY KEY,
    organization_id INTEGER NOT NULL,
    dataset_id INTEGER NOT NULL,
    doi VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
