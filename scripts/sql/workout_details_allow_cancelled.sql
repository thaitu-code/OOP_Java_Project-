-- Sua toi thieu cho CHECK cu da xac minh tren fitness_ai/public.
-- Khong xoa/sua dong du lieu, khong doi FK/index hoac CHECK cua bang khac.
BEGIN;
SET LOCAL lock_timeout = '5s';

ALTER TABLE public.workout_details
    DROP CONSTRAINT workout_details_status_check;

ALTER TABLE public.workout_details
    ADD CONSTRAINT workout_details_status_check CHECK (status IN (
        'NOT_STARTED', 'IN_PROGRESS', 'RESTING', 'PAUSED', 'FROZEN', 'COMPLETED', 'CANCELLED'
    ));

COMMIT;
