-- Additive mechanic profile field; existing experience values are not changed.
SET @roadassist_schema = DATABASE();
SELECT COUNT(*) INTO @roadassist_has_experience
FROM information_schema.columns
WHERE table_schema = @roadassist_schema
  AND table_name = 'users'
  AND column_name = 'experience_years';

SET @roadassist_sql = IF(@roadassist_has_experience = 0,
    'ALTER TABLE users ADD COLUMN experience_years INT NULL',
    'SELECT ''experience_years column already exists; preserving it''');
PREPARE roadassist_stmt FROM @roadassist_sql;
EXECUTE roadassist_stmt;
DEALLOCATE PREPARE roadassist_stmt;
