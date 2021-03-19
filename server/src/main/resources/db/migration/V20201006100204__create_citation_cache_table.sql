CREATE TABLE citation_cache (
    doi TEXT PRIMARY KEY NOT NULL,
    citation TEXT,
    cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT citation_cache_doi_lowercase_check
    CHECK (doi = LOWER(doi))
);
