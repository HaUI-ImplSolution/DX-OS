-- SPDX-License-Identifier: Apache-2.0
\set ON_ERROR_STOP on

-- This script is safe to run both from docker-entrypoint-initdb.d and manually.
SELECT 'CREATE DATABASE dx_farm_ai_vector_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'dx_farm_ai_vector_db'
)\gexec

\connect dx_farm_ai_vector_db

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    source VARCHAR(500),
    document_type VARCHAR(100),
    version VARCHAR(50),
    disease_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_documents_disease_code
    ON documents (disease_code);

CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    page_number INTEGER,
    embedding vector(384)
);

CREATE INDEX IF NOT EXISTS ix_document_chunks_document_id
    ON document_chunks (document_id);

DO $$
DECLARE
    unexpected_tables TEXT;
BEGIN
    SELECT string_agg(tablename, ', ' ORDER BY tablename)
    INTO unexpected_tables
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename NOT IN ('documents', 'document_chunks');

    IF unexpected_tables IS NOT NULL THEN
        RAISE EXCEPTION
            'dx_farm_ai_vector_db contains unexpected public tables: %',
            unexpected_tables;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_attribute AS attribute
        JOIN pg_class AS relation ON relation.oid = attribute.attrelid
        JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = 'public'
          AND relation.relname = 'document_chunks'
          AND attribute.attname = 'embedding'
          AND format_type(attribute.atttypid, attribute.atttypmod) = 'vector(384)'
          AND NOT attribute.attisdropped
    ) THEN
        RAISE EXCEPTION 'document_chunks.embedding must be vector(384)';
    END IF;
END
$$;
