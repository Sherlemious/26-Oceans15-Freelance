DO $$
BEGIN
    CREATE TYPE payout_method AS ENUM ('BANK_TRANSFER', 'PAYPAL', 'CRYPTO');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

DO $$
BEGIN
    CREATE TYPE payout_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

DO $$
BEGIN
    CREATE TYPE discount_type AS ENUM ('PERCENTAGE', 'FIXED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

DO $$
BEGIN
    IF to_regclass('public.payouts') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'payouts'
              AND column_name = 'method'
              AND udt_name <> 'payout_method'
        ) THEN
            ALTER TABLE payouts
                ALTER COLUMN method TYPE payout_method
                USING method::payout_method;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'payouts'
              AND column_name = 'status'
              AND udt_name <> 'payout_status'
        ) THEN
            ALTER TABLE payouts
                ALTER COLUMN status TYPE payout_status
                USING status::payout_status;
        END IF;
    END IF;
END $$;
@@

DO $$
BEGIN
    IF to_regclass('public.promo_codes') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'promo_codes'
              AND column_name = 'discount_type'
              AND udt_name <> 'discount_type'
        ) THEN
            ALTER TABLE promo_codes
                ALTER COLUMN discount_type TYPE discount_type
                USING discount_type::discount_type;
        END IF;
    END IF;
END $$;
@@
