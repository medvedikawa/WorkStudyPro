ALTER TABLE public.assignments
    ADD COLUMN IF NOT EXISTS task_type text NOT NULL DEFAULT 'SCHOOL'
    CHECK (task_type IN ('SCHOOL', 'WORK'));
