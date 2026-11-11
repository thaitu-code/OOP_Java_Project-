-- Apply once on an existing PostgreSQL database if Hibernate schema auto-update is disabled.
-- Nullable on purpose: manually added meal logs do not belong to an AI plan item.
ALTER TABLE meal_details ADD COLUMN IF NOT EXISTS ai_meal_plan_item_id BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS ux_meal_details_ai_plan_item_id
    ON meal_details (ai_meal_plan_item_id)
    WHERE ai_meal_plan_item_id IS NOT NULL;
