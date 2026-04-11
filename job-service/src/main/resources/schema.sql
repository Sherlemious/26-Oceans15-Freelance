DO $$
BEGIN
    CREATE TYPE job_category AS ENUM ('WEB_DEV', 'MOBILE', 'DESIGN', 'WRITING');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

DO $$
BEGIN
    CREATE TYPE job_status AS ENUM ('OPEN', 'IN_PROGRESS', 'CLOSED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

DO $$
BEGIN
    CREATE TYPE job_attachment_type AS ENUM ('BRIEF', 'MOCKUP', 'REFERENCE', 'CONTRACT_TEMPLATE');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

DO $$
BEGIN
    IF to_regclass('public.jobs') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'jobs'
              AND column_name = 'category'
              AND udt_name <> 'job_category'
        ) THEN
            ALTER TABLE jobs
                ALTER COLUMN category TYPE job_category
                USING category::job_category;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'jobs'
              AND column_name = 'status'
              AND udt_name <> 'job_status'
        ) THEN
            ALTER TABLE jobs
                ALTER COLUMN status TYPE job_status
                USING status::job_status;
        END IF;
    END IF;
END $$;
@@

DO $$
BEGIN
    IF to_regclass('public.job_attachments') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'job_attachments'
              AND column_name = 'type'
              AND udt_name <> 'job_attachment_type'
        ) THEN
            ALTER TABLE job_attachments
                ALTER COLUMN type TYPE job_attachment_type
                USING type::job_attachment_type;
        END IF;
    END IF;
END $$;
@@
