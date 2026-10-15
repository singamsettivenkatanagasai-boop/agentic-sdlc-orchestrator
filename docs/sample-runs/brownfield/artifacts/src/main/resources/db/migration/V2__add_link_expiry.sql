-- V2: optional link expiry
-- The prototype lets Hibernate apply this (ddl-auto=update); in production run it with a migration tool.
ALTER TABLE links ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE NULL;
CREATE INDEX idx_links_expires_at ON links (expires_at);
