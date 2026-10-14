-- Catalogue listing is paginated and sorted most often by name, price and
-- availableQuantity; each of these is a linear scan on a large table. The
-- default page-sort uses the PK (id), which the PK index already covers.
--
-- Name indexes serve two distinct predicates:
--   * ORDER BY name            -> plain btree matches (leading-bound, sorted)
--   * search: name LIKE '%kw%' -> the sort btree CANNOT serve a leading
--     wildcard; only trigram matching can. GIN(pg_trgm) on the exact
--     expression the search uses (lower(trim(name)), which equals the
--     whitespace-padded match the btree is asked for) keeps the search from
--     being a full scan. Note that btree on name is still listed: it remains
--     the right index for ORDER BY name pagination.
CREATE INDEX IF NOT EXISTS idx_product_name ON product (name);
CREATE INDEX IF NOT EXISTS idx_product_price ON product (price);
CREATE INDEX IF NOT EXISTS idx_product_available_quantity ON product (available_quantity);
CREATE INDEX IF NOT EXISTS idx_product_category ON product (category_id);

-- pg_trgm ships in postgresql-contrib and is a "trusted" extension (PG >= 13):
-- it can be created by a non-superuser, including on RDS/Cloud SQL.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_product_name_trgm
    ON product USING gin (lower(trim(name)) gin_trgm_ops);
