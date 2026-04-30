BEGIN;

DO $$
BEGIN
    CREATE TYPE public.user_role AS ENUM ('FREELANCER', 'CLIENT', 'ADMIN');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.user_status AS ENUM ('ACTIVE', 'DEACTIVATED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.proficiency_level AS ENUM ('BEGINNER', 'INTERMEDIATE', 'EXPERT');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.job_category AS ENUM ('WEB_DEV', 'MOBILE', 'DESIGN', 'WRITING');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.job_status AS ENUM ('OPEN', 'IN_PROGRESS', 'CLOSED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.job_attachment_type AS ENUM ('BRIEF', 'MOCKUP', 'REFERENCE', 'CONTRACT_TEMPLATE');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.proposal_status AS ENUM ('SUBMITTED', 'SHORTLISTED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.milestone_status AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'APPROVED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.contract_status AS ENUM ('ACTIVE', 'COMPLETED', 'TERMINATED', 'DISPUTED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.payout_method AS ENUM ('BANK_TRANSFER', 'PAYPAL', 'CRYPTO');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.payout_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE public.discount_type AS ENUM ('PERCENTAGE', 'FIXED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    IF to_regclass('public.users') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'users'
              AND column_name = 'role'
              AND udt_name <> 'user_role'
        ) THEN
            ALTER TABLE public.users
                ALTER COLUMN role TYPE public.user_role
                USING role::public.user_role;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'users'
              AND column_name = 'status'
              AND udt_name <> 'user_status'
        ) THEN
            ALTER TABLE public.users
                ALTER COLUMN status TYPE public.user_status
                USING status::public.user_status;
        END IF;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.user_skills') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'user_skills'
              AND column_name = 'proficiency_level'
              AND udt_name <> 'proficiency_level'
        ) THEN
            ALTER TABLE public.user_skills
                ALTER COLUMN proficiency_level TYPE public.proficiency_level
                USING proficiency_level::public.proficiency_level;
        END IF;
    END IF;
END $$;

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
            ALTER TABLE public.jobs
                ALTER COLUMN category TYPE public.job_category
                USING category::public.job_category;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'jobs'
              AND column_name = 'status'
              AND udt_name <> 'job_status'
        ) THEN
            ALTER TABLE public.jobs
                ALTER COLUMN status TYPE public.job_status
                USING status::public.job_status;
        END IF;
    END IF;
END $$;

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
            ALTER TABLE public.job_attachments
                ALTER COLUMN "type" TYPE public.job_attachment_type
                USING "type"::public.job_attachment_type;
        END IF;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.proposals') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'proposals'
              AND column_name = 'status'
              AND udt_name <> 'proposal_status'
        ) THEN
            ALTER TABLE public.proposals
                ALTER COLUMN status TYPE public.proposal_status
                USING status::public.proposal_status;
        END IF;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.proposal_milestones') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'proposal_milestones'
              AND column_name = 'status'
              AND udt_name <> 'milestone_status'
        ) THEN
            ALTER TABLE public.proposal_milestones
                ALTER COLUMN status TYPE public.milestone_status
                USING status::public.milestone_status;
        END IF;
    END IF;
END $$;

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
            ALTER TABLE public.contracts
                ALTER COLUMN status TYPE public.contract_status
                USING status::public.contract_status;
        END IF;
    END IF;
END $$;

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
            ALTER TABLE public.payouts
                ALTER COLUMN method TYPE public.payout_method
                USING method::public.payout_method;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'payouts'
              AND column_name = 'status'
              AND udt_name <> 'payout_status'
        ) THEN
            ALTER TABLE public.payouts
                ALTER COLUMN status TYPE public.payout_status
                USING status::public.payout_status;
        END IF;
    END IF;
END $$;

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
            ALTER TABLE public.promo_codes
                ALTER COLUMN discount_type TYPE public.discount_type
                USING discount_type::public.discount_type;
        END IF;
    END IF;
END $$;

COMMIT;
