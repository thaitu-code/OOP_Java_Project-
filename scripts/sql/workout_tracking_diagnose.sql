-- CHI DOC: chay tren cung datasource voi ung dung, gui ket qua CHECK/NOT NULL.
SELECT current_database() AS database_name, current_schema() AS schema_name,
       current_setting('search_path') AS search_path;

SELECT c.conrelid::regclass AS table_name, c.conname AS constraint_name,
       pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c
WHERE c.contype = 'c'
  AND c.conrelid IN (to_regclass('public.workout_details'), to_regclass('public.workout_sessions'))
ORDER BY table_name, constraint_name;

SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('workout_details', 'workout_sessions')
  AND column_name IN ('status', 'started_at', 'completed_at', 'cancelled_at', 'start_time', 'end_time')
ORDER BY table_name, ordinal_position;

SELECT session_id, user_id, status, start_time, end_time
FROM public.workout_sessions WHERE session_id = 4;

SELECT detail_id, session_id, status, started_at, completed_at, cancelled_at
FROM public.workout_details WHERE session_id = 4 ORDER BY detail_id;

-- Metadata cho nghiem thu, khong tra thong tin dang nhap hoac noi dung chat.
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN ('workout_sessions', 'workout_details')
ORDER BY tablename, indexname;

SELECT c.conname AS constraint_name, pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c
WHERE c.contype = 'f' AND c.conrelid = to_regclass('public.workout_details')
ORDER BY c.conname;

-- Chi cac tai khoan test da xuat hien trong report; khong doc can nang thuc.
SELECT d.detail_id, d.session_id,
       d.duration_minutes = ROUND((d.active_duration_millis::numeric + d.rest_duration_millis::numeric) / 60000, 1)
           AS duration_matches,
       d.calories_burned = ROUND(d.met_value_snapshot * 3.5 * d.body_weight_kg_snapshot
           * d.active_duration_millis::numeric / 12000000, 1) AS calories_matches,
       d.active_duration_millis::numeric + d.rest_duration_millis::numeric
           >= d.min_duration_minutes_snapshot * 60000 AS minimum_met,
       d.sets IS NULL OR d.sets >= 0 AS valid_sets
FROM public.workout_details d
JOIN public.workout_sessions w ON w.session_id = d.session_id
WHERE w.user_id IN (1, 11) AND d.status = 'COMPLETED'
  AND LEFT(d.exercise_name_snapshot, 8) = 'TEST_WT_'
ORDER BY d.detail_id DESC LIMIT 12;

SELECT w.session_id, w.status,
       COUNT(d.detail_id) FILTER (WHERE d.status = 'COMPLETED') AS completed_details,
       COUNT(d.detail_id) FILTER (WHERE d.status = 'CANCELLED') AS cancelled_details,
       COUNT(d.detail_id) FILTER (WHERE d.status NOT IN ('COMPLETED', 'CANCELLED')) AS unfinished_details,
       w.end_time IS NOT NULL AS has_end_time
FROM public.workout_sessions w
LEFT JOIN public.workout_details d ON d.session_id = w.session_id
WHERE w.user_id IN (1, 11) AND w.status IN ('COMPLETED', 'CANCELLED')
GROUP BY w.session_id, w.status, w.end_time
ORDER BY w.session_id DESC LIMIT 8;

SELECT user_id, COUNT(*) AS open_workouts
FROM public.workout_sessions
WHERE user_id IN (1, 11) AND status = 'IN_PROGRESS'
GROUP BY user_id;

SELECT
    (SELECT COUNT(*) FROM (
        SELECT user_id FROM public.workout_sessions WHERE status = 'IN_PROGRESS'
        GROUP BY user_id HAVING COUNT(*) > 1
    ) duplicate_open) AS users_with_multiple_open_workouts,
    (SELECT COUNT(*) FROM (
        SELECT session_id, exercise_id FROM public.workout_details WHERE exercise_id IS NOT NULL
        GROUP BY session_id, exercise_id HAVING COUNT(*) > 1
    ) duplicate_exercises) AS duplicate_exercise_groups,
    (SELECT COUNT(*) FROM public.workout_details
        WHERE approved_rest_seconds_snapshot IS NOT NULL
          AND approved_rest_seconds_snapshot NOT BETWEEN 1 AND 300) AS invalid_approved_rest_rows;

-- Phan biet truong snapshot thieu cua cac bai do tu phien ban cu.
SELECT d.detail_id, d.session_id, w.user_id, d.status,
       d.min_duration_minutes_snapshot IS NULL AS missing_min_duration_snapshot,
       d.met_value_snapshot IS NULL AS missing_met_snapshot,
       d.started_at IS NOT NULL AS has_started_at
FROM public.workout_details d
JOIN public.workout_sessions w ON w.session_id = d.session_id
WHERE d.status IN ('IN_PROGRESS', 'RESTING', 'PAUSED', 'FROZEN')
  AND (d.min_duration_minutes_snapshot IS NULL OR d.met_value_snapshot IS NULL)
ORDER BY d.detail_id LIMIT 20;
