-- Helper to clear contents of citation cache

CREATE OR REPLACE FUNCTION purge_citation_cache()
RETURNS BIGINT AS $$
  WITH deleted AS (
    DELETE FROM citation_cache RETURNING *
  )
  SELECT COUNT(*)
  FROM deleted
$$ LANGUAGE SQL
SET search_path FROM CURRENT;
