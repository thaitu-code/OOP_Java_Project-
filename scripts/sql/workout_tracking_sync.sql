-- Chay thu cong tren database phat trien TRUOC khi kiem thu lifecycle moi.
-- Khong tu xoa/sua cac dong lich su, khong tu dien snapshot con thieu.
-- Hai truy van dau phai khong tra dong trung truoc khi tao unique index.
SELECT user_id, COUNT(*) AS open_workouts
FROM public.workout_sessions WHERE status = 'IN_PROGRESS'
GROUP BY user_id HAVING COUNT(*) > 1;

SELECT session_id, exercise_id, COUNT(*) AS duplicate_details
FROM public.workout_details WHERE exercise_id IS NOT NULL
GROUP BY session_id, exercise_id HAVING COUNT(*) > 1;

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE public.workout_details
    ADD COLUMN IF NOT EXISTS started_at timestamptz,
    ADD COLUMN IF NOT EXISTS completed_at timestamptz,
    ADD COLUMN IF NOT EXISTS cancelled_at timestamptz,
    ADD COLUMN IF NOT EXISTS active_duration_millis bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rest_duration_millis bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_recorded_at timestamptz,
    ADD COLUMN IF NOT EXISTS last_heartbeat_at timestamptz,
    ADD COLUMN IF NOT EXISTS rest_started_at timestamptz,
    ADD COLUMN IF NOT EXISTS rest_ends_at timestamptz,
    ADD COLUMN IF NOT EXISTS timer_token uuid,
    ADD COLUMN IF NOT EXISTS min_duration_minutes_snapshot numeric(6,1),
    ADD COLUMN IF NOT EXISTS body_weight_kg_snapshot numeric(5,2),
    ADD COLUMN IF NOT EXISTS approved_rest_seconds_snapshot integer;

-- Hibernate ddl-auto=update khong dam bao doi CHECK cua enum da ton tai.
-- Chi thay CHECK chi tham chieu rieng cot status tren hai bang workout.
DO $$
DECLARE status_check record;
BEGIN
    FOR status_check IN
        SELECT c.conname, c.conrelid::regclass AS target_table
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'status'
        WHERE c.conrelid IN ('public.workout_details'::regclass, 'public.workout_sessions'::regclass)
          AND c.contype = 'c' AND c.conkey = ARRAY[a.attnum]::smallint[]
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', status_check.target_table, status_check.conname);
    END LOOP;
END $$;

ALTER TABLE public.workout_details
    ADD CONSTRAINT ck_workout_details_status CHECK (status IN (
        'NOT_STARTED', 'IN_PROGRESS', 'RESTING', 'PAUSED', 'FROZEN', 'COMPLETED', 'CANCELLED'
    ));

ALTER TABLE public.workout_sessions
    ADD CONSTRAINT ck_workout_sessions_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

ALTER TABLE public.workout_details
    DROP CONSTRAINT IF EXISTS ck_workout_details_approved_rest;
ALTER TABLE public.workout_details
    ADD CONSTRAINT ck_workout_details_approved_rest
        CHECK (approved_rest_seconds_snapshot IS NULL OR approved_rest_seconds_snapshot BETWEEN 1 AND 300);

CREATE UNIQUE INDEX IF NOT EXISTS uq_workout_sessions_one_open_per_user
    ON public.workout_sessions (user_id) WHERE status = 'IN_PROGRESS';
CREATE UNIQUE INDEX IF NOT EXISTS uq_workout_details_session_exercise
    ON public.workout_details (session_id, exercise_id);
CREATE INDEX IF NOT EXISTS idx_workout_sessions_user_start
    ON public.workout_sessions (user_id, start_time DESC, session_id DESC);
CREATE INDEX IF NOT EXISTS idx_workout_details_session_status
    ON public.workout_details (session_id, status);
CREATE INDEX IF NOT EXISTS idx_workout_details_active_heartbeat
    ON public.workout_details (last_heartbeat_at, session_id) WHERE status = 'IN_PROGRESS';

COMMIT;

-- Chi kiem tra, khong tu backfill snapshot/ghi nhan bai cu thanh hoan thanh.
SELECT detail_id, session_id, status
FROM public.workout_details
WHERE status IN ('IN_PROGRESS', 'RESTING', 'PAUSED', 'FROZEN')
  AND (min_duration_minutes_snapshot IS NULL OR met_value_snapshot IS NULL);
