DO $$
BEGIN
    CREATE TYPE proposal_status AS ENUM ('SUBMITTED', 'SHORTLISTED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

DO $$
BEGIN
    CREATE TYPE milestone_status AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'APPROVED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

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
            ALTER TABLE proposals
                ALTER COLUMN status TYPE proposal_status
                USING status::proposal_status;
        END IF;
    END IF;
END $$;
@@

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
            ALTER TABLE proposal_milestones
                ALTER COLUMN status TYPE milestone_status
                USING status::milestone_status;
        END IF;
    END IF;
END $$;
@@
