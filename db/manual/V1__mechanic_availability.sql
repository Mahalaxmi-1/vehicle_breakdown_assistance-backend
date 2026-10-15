-- RoadAssist additive migration: mechanic availability.
-- Prerequisite: MySQL 8.0.16+ (enforced CHECK constraints).
-- Check that any existing availability column is VARCHAR(20) and nullable,
-- and that any same-named check has the expression shown below before running.
-- This migration intentionally does not delete user rows.

SET @roadassist_schema = DATABASE();
SELECT COUNT(*) INTO @roadassist_has_availability
FROM information_schema.columns
WHERE table_schema = @roadassist_schema
  AND table_name = 'users'
  AND column_name = 'availability';

SET @roadassist_sql = IF(@roadassist_has_availability = 0,
    'ALTER TABLE users ADD COLUMN availability VARCHAR(20) NULL',
    'SELECT ''availability column already exists; preserving it''');
PREPARE roadassist_stmt FROM @roadassist_sql;
EXECUTE roadassist_stmt;
DEALLOCATE PREPARE roadassist_stmt;

-- Backfill only unset mechanic values. Owners/admins remain NULL.
UPDATE users
SET availability = 'OFFLINE'
WHERE role = 'MECHANIC' AND availability IS NULL;

SELECT COUNT(*) INTO @roadassist_has_check
FROM information_schema.table_constraints
WHERE constraint_schema = @roadassist_schema
  AND table_name = 'users'
  AND constraint_name = 'chk_users_availability'
  AND constraint_type = 'CHECK';

SET @roadassist_sql = IF(@roadassist_has_check = 0,
    'ALTER TABLE users ADD CONSTRAINT chk_users_availability CHECK (availability IS NULL OR availability IN (''AVAILABLE'', ''BUSY'', ''OFFLINE''))',
    'SELECT ''availability check already exists; verify its expression and enforcement''');
PREPARE roadassist_stmt FROM @roadassist_sql;
EXECUTE roadassist_stmt;
DEALLOCATE PREPARE roadassist_stmt;
