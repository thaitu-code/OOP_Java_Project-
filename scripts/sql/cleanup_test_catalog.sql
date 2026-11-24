-- Chay ca file trong CUNG MOT connection PostgreSQL, tren dung database ung dung.
-- Dung Spring Boot va sao luu database truoc khi chay.
-- Mac dinh la chay thu: ROLLBACK o cuoi file hoan lai tat ca DELETE.
-- Chi doi ROLLBACK cuoi file thanh COMMIT sau khi da xem va dong y ket qua.
-- Khong xoa users, buoi/ngay nhat ky cha, check-in, streak, badge hay ke hoach AI.

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

LOCK TABLE public.categories, public.exercises, public.exercise_levels,
           public.food_categories, public.foods,
           public.workout_details, public.meal_details
IN SHARE ROW EXCLUSIVE MODE;

-- Dung ID da duoc nguoi dung xac nhan, dong thoi kiem tra tien to ten mau.
CREATE TEMP TABLE cleanup_exercises ON COMMIT DROP AS
SELECT exercise_id, exercise_name
FROM public.exercises
WHERE exercise_id IN (2, 10)
  AND LEFT(exercise_name, 8) = 'TEST_WT_';

CREATE TEMP TABLE cleanup_exercise_categories ON COMMIT DROP AS
SELECT category_id, category_name
FROM public.categories
WHERE category_id = 1
  AND LEFT(category_name, 8) = 'TEST_WT_';

CREATE TEMP TABLE cleanup_foods ON COMMIT DROP AS
SELECT food_id, food_name
FROM public.foods
WHERE food_id IN (1, 2, 3, 4, 5, 6, 7)
  AND LEFT(food_name, 9) = 'TEST_NUT_';

CREATE TEMP TABLE cleanup_food_categories ON COMMIT DROP AS
SELECT category_id, category_name
FROM public.food_categories
WHERE category_id IN (1, 2, 3, 4, 5)
  AND LEFT(category_name, 9) = 'TEST_NUT_';

DO $guard$
DECLARE
    linked_to_ai boolean;
    unexpected_fks text;
BEGIN
    IF (SELECT COUNT(*) FROM cleanup_exercises) <> 2
       OR (SELECT COUNT(*) FROM cleanup_exercise_categories) <> 1
       OR (SELECT COUNT(*) FROM cleanup_foods) <> 7
       OR (SELECT COUNT(*) FROM cleanup_food_categories) <> 5 THEN
        RAISE EXCEPTION 'ID/ten da thay doi so voi 15 muc da xac nhan. Khong xoa; kiem tra lai danh sach.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.exercises e
        WHERE e.category_id IN (SELECT category_id FROM cleanup_exercise_categories)
          AND e.exercise_id NOT IN (SELECT exercise_id FROM cleanup_exercises)
    ) THEN
        RAISE EXCEPTION 'Nhom bai tap mau con chua bai NGOAI danh sach 2,10. Khong xoa nhom.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.foods f
        WHERE f.category_id IN (SELECT category_id FROM cleanup_food_categories)
          AND f.food_id NOT IN (SELECT food_id FROM cleanup_foods)
    ) THEN
        RAISE EXCEPTION 'Nhom thuc pham mau con chua Food NGOAI danh sach 1-7. Khong xoa nhom.';
    END IF;

    IF to_regclass('public.ai_recommended_exercises') IS NOT NULL THEN
        EXECUTE 'LOCK TABLE public.ai_recommended_exercises IN SHARE ROW EXCLUSIVE MODE';
        EXECUTE 'SELECT EXISTS (
            SELECT 1 FROM public.ai_recommended_exercises
            WHERE exercise_id IN (SELECT exercise_id FROM pg_temp.cleanup_exercises)
        )' INTO linked_to_ai;
        IF linked_to_ai THEN
            RAISE EXCEPTION 'Ke hoach AI dang tham chieu bai tap mau. Can doi/bo cac bai nay trong ke hoach truoc.';
        END IF;
    END IF;

    IF to_regclass('public.ai_meal_plan_items') IS NOT NULL THEN
        EXECUTE 'LOCK TABLE public.ai_meal_plan_items IN SHARE ROW EXCLUSIVE MODE';
        EXECUTE 'SELECT EXISTS (
            SELECT 1 FROM public.ai_meal_plan_items
            WHERE food_id IN (SELECT food_id FROM pg_temp.cleanup_foods)
        )' INTO linked_to_ai;
        IF linked_to_ai THEN
            RAISE EXCEPTION 'Ke hoach AI dang tham chieu Food mau. Can doi/bo cac mon nay trong ke hoach truoc.';
        END IF;
    END IF;

    -- Chan schema co FK moi/chua biet, ke ca FK ON DELETE CASCADE.
    SELECT string_agg(format('%s -> %s [%I]', conrelid::regclass,
                              confrelid::regclass, conname), ', ')
    INTO unexpected_fks
    FROM pg_constraint
    WHERE contype = 'f'
      AND confrelid IN (
          'public.categories'::regclass, 'public.exercises'::regclass,
          'public.exercise_levels'::regclass, 'public.food_categories'::regclass,
          'public.foods'::regclass, 'public.workout_details'::regclass,
          'public.meal_details'::regclass
      )
      AND NOT (
          (confrelid = 'public.categories'::regclass
              AND conrelid = 'public.exercises'::regclass)
          OR (confrelid = 'public.exercises'::regclass
              AND conrelid IN ('public.workout_details'::regclass,
                               'public.exercise_levels'::regclass))
          OR (confrelid = 'public.exercises'::regclass
              AND conrelid = COALESCE(to_regclass('public.ai_recommended_exercises'), 0::regclass))
          OR (confrelid = 'public.food_categories'::regclass
              AND conrelid = 'public.foods'::regclass)
          OR (confrelid = 'public.foods'::regclass
              AND conrelid = 'public.meal_details'::regclass)
          OR (confrelid = 'public.foods'::regclass
              AND conrelid = COALESCE(to_regclass('public.ai_meal_plan_items'), 0::regclass))
      );

    IF unexpected_fks IS NOT NULL THEN
        RAISE EXCEPTION 'Co FK ngoai pham vi script; khong xoa: %', unexpected_fks;
    END IF;
END;
$guard$;

-- Bao cao cac ID/name sap duoc xoa.
SELECT 'Bai tap' AS loai, exercise_id AS id, exercise_name AS ten FROM cleanup_exercises
UNION ALL
SELECT 'Nhom bai tap', category_id, category_name FROM cleanup_exercise_categories
UNION ALL
SELECT 'Thuc pham', food_id, food_name FROM cleanup_foods
UNION ALL
SELECT 'Nhom thuc pham', category_id, category_name FROM cleanup_food_categories
ORDER BY loai, id;

CREATE TEMP TABLE cleanup_report (loai text, so_ban_ghi bigint) ON COMMIT DROP;

-- Xoa cac dong lich su tham chieu dung bai mau.
-- Gom ca lich su da tach FK khi xoa master truoc day, neu snapshot cung ID va ten mau.
WITH removed AS (
    DELETE FROM public.workout_details d
    WHERE d.exercise_id IN (SELECT exercise_id FROM cleanup_exercises)
       OR (d.exercise_id_snapshot IN (SELECT exercise_id FROM cleanup_exercises)
           AND LEFT(d.exercise_name_snapshot, 8) = 'TEST_WT_')
    RETURNING detail_id
)
INSERT INTO cleanup_report SELECT 'Chi tiet lich su tap', COUNT(*) FROM removed;

WITH removed AS (
    DELETE FROM public.meal_details
    WHERE food_id IN (SELECT food_id FROM cleanup_foods)
    RETURNING meal_detail_id
)
INSERT INTO cleanup_report SELECT 'Chi tiet nhat ky an uong', COUNT(*) FROM removed;

WITH removed AS (
    DELETE FROM public.exercise_levels
    WHERE exercise_id IN (SELECT exercise_id FROM cleanup_exercises)
    RETURNING id
)
INSERT INTO cleanup_report SELECT 'Cap do cua bai tap mau', COUNT(*) FROM removed;

WITH removed AS (
    DELETE FROM public.exercises
    WHERE exercise_id IN (SELECT exercise_id FROM cleanup_exercises)
    RETURNING exercise_id
)
INSERT INTO cleanup_report SELECT 'Bai tap', COUNT(*) FROM removed;

WITH removed AS (
    DELETE FROM public.categories
    WHERE category_id IN (SELECT category_id FROM cleanup_exercise_categories)
    RETURNING category_id
)
INSERT INTO cleanup_report SELECT 'Nhom bai tap', COUNT(*) FROM removed;

WITH removed AS (
    DELETE FROM public.foods
    WHERE food_id IN (SELECT food_id FROM cleanup_foods)
    RETURNING food_id
)
INSERT INTO cleanup_report SELECT 'Thuc pham', COUNT(*) FROM removed;

WITH removed AS (
    DELETE FROM public.food_categories
    WHERE category_id IN (SELECT category_id FROM cleanup_food_categories)
    RETURNING category_id
)
INSERT INTO cleanup_report SELECT 'Nhom thuc pham', COUNT(*) FROM removed;

SELECT * FROM cleanup_report ORDER BY loai;

-- XAC MINH: ca bon so dem phai bang 0 trong transaction nay.
SELECT
    (SELECT COUNT(*) FROM public.exercises
     WHERE exercise_id IN (SELECT exercise_id FROM cleanup_exercises)) AS bai_tap_con_lai,
    (SELECT COUNT(*) FROM public.categories
     WHERE category_id IN (SELECT category_id FROM cleanup_exercise_categories)) AS nhom_bai_tap_con_lai,
    (SELECT COUNT(*) FROM public.foods
     WHERE food_id IN (SELECT food_id FROM cleanup_foods)) AS thuc_pham_con_lai,
    (SELECT COUNT(*) FROM public.food_categories
     WHERE category_id IN (SELECT category_id FROM cleanup_food_categories)) AS nhom_thuc_pham_con_lai;

-- LAN DAU: giu ROLLBACK de chay thu va xem cac result set.
-- XOA THAT: doi duy nhat dong ROLLBACK duoi day thanh COMMIT, roi chay lai CA FILE.
ROLLBACK;
