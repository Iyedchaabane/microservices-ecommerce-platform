-- Product.stock is changing from double precision (DB) / double (Java) to
-- INTEGER (DB) / int (Java):
--   * everything that reads/writes availableQuantity (DTOs, entity, purchase
--     service, order-service Purchase interaction) now speaks whole units;
--   * the purchase guard "requested > stock" was double-comparison
--     (5.5 - 2.5 drift artifacts) and becomes exact integer comparison.
--
-- Data guard: a cast of a fractional double to the new INT column would
-- either fail (PG strict) or silently TRUNCATE (H2 / some drivers). Neither
-- is acceptable for stock data: fractional values are real legacy data
-- written by the previous double-based release, and no automatic value is
-- "safe" to invent for them. So, exactly like the V3 price guard: fail fast
-- with a precise count instead of corrupting or clipping data. The operator
-- must correct those rows before re-running the migration (values such as
-- 5.5 must be reconciled by the business, e.g. rounded via a data fix).
--
-- The cast itself is exact for every whole-number double in int range
-- (stock values above 2^31 are not a plausible business case; the guard
-- only rejects fractional stock, out-of-range ints would still fail the
-- cast with a numeric out-of-range error from the DB itself, which is the
-- same "operator must fix the data" outcome).
DO $$
DECLARE
    fractional_rows bigint;
BEGIN
    SELECT count(*) INTO fractional_rows
    FROM product
    WHERE available_quantity <> floor(available_quantity);

    IF fractional_rows > 0 THEN
        RAISE EXCEPTION
            'product.available_quantity contains % row(s) with fractional stock; reconcile them to whole units before migrating',
            fractional_rows
            USING ERRCODE = '23502';
    END IF;
END
$$;

ALTER TABLE product
    ALTER COLUMN available_quantity TYPE integer
    USING available_quantity::int;

-- The column was NOT NULL in the original create-table; keep that invariant.
ALTER TABLE product
    ALTER COLUMN available_quantity SET NOT NULL;
