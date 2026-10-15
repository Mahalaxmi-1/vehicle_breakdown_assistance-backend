-- Additive service completion details. Existing values, if present, are kept.
SET @roadassist_schema = DATABASE();
SELECT COUNT(*) INTO @roadassist_has_completion_notes
FROM information_schema.columns
WHERE table_schema = @roadassist_schema
  AND table_name = 'assistance_requests'
  AND column_name = 'completion_notes';

SET @roadassist_sql = IF(@roadassist_has_completion_notes = 0,
    'ALTER TABLE assistance_requests ADD COLUMN completion_notes VARCHAR(1000) NULL',
    'SELECT ''completion_notes column already exists; preserving it''');
PREPARE roadassist_stmt FROM @roadassist_sql;
EXECUTE roadassist_stmt;
DEALLOCATE PREPARE roadassist_stmt;
