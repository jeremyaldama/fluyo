-- HU-11: allow deleting a category that past expenses reference.
-- The original FK used the default NO ACTION, so deleting an in-use category failed.
-- Switch it to ON DELETE SET NULL: deleting a category keeps the expense, just clears
-- its category (the app renders such rows as "Sin categoría").

-- Drop the existing expenses→categories FK regardless of its generated name.
DO $$
DECLARE c text;
BEGIN
    SELECT conname INTO c
    FROM pg_constraint
    WHERE conrelid = 'expenses'::regclass
      AND contype = 'f'
      AND confrelid = 'categories'::regclass
    LIMIT 1;
    IF c IS NOT NULL THEN
        EXECUTE format('ALTER TABLE expenses DROP CONSTRAINT %I', c);
    END IF;
END $$;

ALTER TABLE expenses
    ADD CONSTRAINT expenses_category_id_fkey
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;
