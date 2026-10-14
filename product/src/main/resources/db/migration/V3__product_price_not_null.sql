-- price is required for every product; Bean Validation (@NotNull on
-- ProductRequest.price) now enforces it at the API edge, so the column must
-- not accept nulls either.
--
-- Safety guard: instead of letting the NOT NULL constraint abort with a
-- generic failure, fail fast with a precise message if any pre-existing row
-- has a NULL price. Those rows are legacy records written before price was
-- enforced; there is no safe automatic value for them (inventing a price
-- would silently corrupt catalogue data), so the operator must fix the data
-- explicitly. Runs in this script's transaction; on DO failure the whole
-- migration aborts before the constraint is applied.
DO $$
DECLARE
    null_price_rows bigint;
BEGIN
    SELECT count(*) INTO null_price_rows
    FROM product
    WHERE price IS NULL;

    IF null_price_rows > 0 THEN
        RAISE EXCEPTION
            'product.price contains % row(s) with NULL; set explicit prices before migrating',
            null_price_rows
            USING ERRCODE = '23502';
    END IF;
END
$$;

ALTER TABLE product
    ALTER COLUMN price SET NOT NULL;
