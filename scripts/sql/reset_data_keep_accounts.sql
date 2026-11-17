-- BAN XOA THAT, KET THUC BANG COMMIT.
-- GIU: users, role/roles, user_roles, user_profile(s), verification_token(s), cac bang phien dang nhap neu co.
-- XOA: tat ca bang public con lai, bao gom catalog, lich su, AI, streak, badges.
-- Giu cau truc bang/FK. Chi reset sequence cua cac bang bi xoa du lieu.
-- Sao luu database, dung Spring Boot, chon dung project/branch/database.
-- Neu Neon dang Failed transaction, bam ROLLBACK truoc. Chay TOAN BO file.
-- Khong dung CASCADE: neu co FK tu bang duoc giu, lenh se dung thay vi xoa lan.

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TEMP TABLE reset_keep_accounts_report (
    table_name text,
    action text,
    before_rows bigint,
    after_rows bigint
) ON COMMIT DROP;

DO $reset$
DECLARE
    keep_tables constant text[] := ARRAY[
        'users', 'role', 'roles', 'user_roles',
        'user_profile', 'user_profiles',
        'verification_token', 'verification_tokens',
        'spring_session', 'spring_session_attributes', 'persistent_logins'
    ];
    table_list text;
    target record;
    backup_name text;
    row_count bigint;
    account_changed boolean;
BEGIN
    IF to_regclass('public.users') IS NULL
       OR to_regclass('public.user_roles') IS NULL
       OR (to_regclass('public.role') IS NULL AND to_regclass('public.roles') IS NULL) THEN
        RAISE EXCEPTION 'Khong tim du bang tai khoan users/user_roles/role(s). Kiem tra lai database; khong xoa.';
    END IF;

    IF to_regclass('public.user_profile') IS NULL
       AND to_regclass('public.user_profiles') IS NULL THEN
        RAISE EXCEPTION 'Khong tim thay bang ho so user_profile(s). Kiem tra lai ten bang; khong xoa.';
    END IF;

    -- Luu ban doi chieu tam cua cac bang duoc giu; khong in mat khau/token ra ket qua.
    FOR target IN
        SELECT schemaname, tablename
        FROM pg_tables
        WHERE schemaname = 'public'
        ORDER BY tablename
    LOOP
        IF target.tablename = ANY(keep_tables) THEN
            EXECUTE format('LOCK TABLE %I.%I IN SHARE MODE', target.schemaname, target.tablename);
            backup_name := 'reset_saved_' || target.tablename;
            EXECUTE format('CREATE TEMP TABLE %I ON COMMIT DROP AS SELECT * FROM %I.%I',
                           backup_name, target.schemaname, target.tablename);
        END IF;

        EXECUTE format('SELECT COUNT(*) FROM %I.%I', target.schemaname, target.tablename)
        INTO row_count;
        INSERT INTO pg_temp.reset_keep_accounts_report VALUES (
            format('%I.%I', target.schemaname, target.tablename),
            CASE WHEN target.tablename = ANY(keep_tables) THEN 'GIU NGUYEN' ELSE 'XOA DU LIEU' END,
            row_count, NULL
        );
    END LOOP;

    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ' ORDER BY tablename)
    INTO table_list
    FROM pg_tables
    WHERE schemaname = 'public'
      AND NOT (tablename = ANY(keep_tables));

    IF table_list IS NOT NULL THEN
        EXECUTE 'TRUNCATE TABLE ' || table_list || ' RESTART IDENTITY RESTRICT';
    END IF;

    FOR target IN
        SELECT schemaname, tablename
        FROM pg_tables
        WHERE schemaname = 'public'
        ORDER BY tablename
    LOOP
        EXECUTE format('SELECT COUNT(*) FROM %I.%I', target.schemaname, target.tablename)
        INTO row_count;

        IF target.tablename = ANY(keep_tables) THEN
            backup_name := 'reset_saved_' || target.tablename;
            -- Doi chieu ca du lieu tung dong cua tai khoan/ho so, khong chi so luong.
            EXECUTE format($compare$
                SELECT EXISTS (
                    (SELECT * FROM %I.%I EXCEPT ALL SELECT * FROM pg_temp.%I)
                    UNION ALL
                    (SELECT * FROM pg_temp.%I EXCEPT ALL SELECT * FROM %I.%I)
                )
            $compare$, target.schemaname, target.tablename, backup_name,
                       backup_name, target.schemaname, target.tablename)
            INTO account_changed;

            IF account_changed THEN
                RAISE EXCEPTION 'Du lieu duoc giu trong bang % da thay doi. Khong commit; can ROLLBACK.',
                                target.tablename;
            END IF;
        ELSIF row_count <> 0 THEN
            RAISE EXCEPTION 'Bang % van con % dong sau reset. Khong commit; can ROLLBACK.',
                            target.tablename, row_count;
        END IF;

        UPDATE pg_temp.reset_keep_accounts_report
        SET after_rows = row_count
        WHERE table_name = format('%I.%I', target.schemaname, target.tablename);
    END LOOP;
END;
$reset$;

-- GIU NGUYEN: before_rows = after_rows va da doi chieu noi dung tung dong.
-- XOA DU LIEU: after_rows = 0.
SELECT current_database() AS database_dang_dung, *
FROM pg_temp.reset_keep_accounts_report
ORDER BY action, table_name;

COMMIT;

-- Sau khi chay lai app, DefaultBadgeInitializer se tao lai 6 huy hieu mac dinh.
-- Tai khoan, quyen va toan bo ho so the chat hien co duoc giu nguyen.
