DO $$
BEGIN
    CREATE TYPE contract_status AS ENUM ('ACTIVE', 'COMPLETED', 'TERMINATED', 'DISPUTED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$@@

DO $$
BEGIN
    IF to_regclass('public.contracts') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'contracts'
              AND column_name = 'status'
              AND udt_name <> 'contract_status'
        ) THEN
            ALTER TABLE contracts
                ALTER COLUMN status TYPE contract_status
                USING status::contract_status;
        END IF;
    END IF;
END $$@@
