DO $$
BEGIN
    CREATE TYPE user_role AS ENUM ('FREELANCER', 'CLIENT', 'ADMIN');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

DO $$
BEGIN
    CREATE TYPE user_status AS ENUM ('ACTIVE', 'DEACTIVATED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

DO $$
BEGIN
    CREATE TYPE proficiency_level AS ENUM ('BEGINNER', 'INTERMEDIATE', 'EXPERT');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
@@

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
            ALTER TABLE users
                ALTER COLUMN role TYPE user_role
                USING role::user_role;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'users'
              AND column_name = 'status'
              AND udt_name <> 'user_status'
        ) THEN
            ALTER TABLE users
                ALTER COLUMN status TYPE user_status
                USING status::user_status;
        END IF;
    END IF;
END $$;
@@

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
            ALTER TABLE user_skills
                ALTER COLUMN proficiency_level TYPE proficiency_level
                USING proficiency_level::proficiency_level;
        END IF;
    END IF;
END $$;
@@
