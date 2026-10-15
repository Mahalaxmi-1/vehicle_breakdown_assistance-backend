USE roadassist_db;

-- Verify the target database and required tables.
SET @ra_ready = IF(DATABASE() = 'roadassist_db', 1, 0);

SELECT COUNT(*) INTO @ra_tables
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE'
  AND table_name IN ('users', 'assistance_requests');

SET @ra_ready = IF(@ra_ready = 1 AND @ra_tables = 2, 1, 0);

-- Reject existing coordinate columns with unexpected definitions.
SELECT COUNT(*) INTO @ra_bad_columns
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'users'
      AND column_name IN ('mechanic_latitude', 'mechanic_longitude'))
    OR
    (table_name = 'assistance_requests'
      AND column_name IN ('latitude', 'longitude'))
  )
  AND NOT (
    data_type = 'decimal'
    AND numeric_precision = 9
    AND numeric_scale = 6
    AND is_nullable = 'YES'
    AND column_default IS NULL
  );

SET @ra_ready = IF(@ra_ready = 1 AND @ra_bad_columns = 0, 1, 0);

-- Reject existing coordinate constraint names rather than guessing their meaning.
SELECT COUNT(*) INTO @ra_existing_checks
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND (
    (table_name = 'users'
      AND constraint_name = 'chk_users_mechanic_coordinates')
    OR
    (table_name = 'assistance_requests'
      AND constraint_name = 'chk_assistance_request_coordinates')
  );

SET @ra_ready = IF(@ra_ready = 1 AND @ra_existing_checks = 0, 1, 0);

SELECT DATABASE() AS selected_database,
       @ra_tables AS required_tables_found,
       @ra_bad_columns AS mismatched_existing_columns,
       @ra_existing_checks AS existing_coordinate_constraint_names,
       @ra_ready AS preflight_passed;

-- Fail before any ALTER if a precondition is not satisfied.
DROP TEMPORARY TABLE IF EXISTS ra_schema_preflight;
CREATE TEMPORARY TABLE ra_schema_preflight (ok TINYINT NOT NULL);
INSERT INTO ra_schema_preflight VALUES (IF(@ra_ready = 1, 1, NULL));
DROP TEMPORARY TABLE ra_schema_preflight;

-- Add only absent coordinate columns.
SET @ra_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'users'
     AND column_name = 'mechanic_latitude') = 0,
  'ALTER TABLE users ADD COLUMN mechanic_latitude DECIMAL(9,6) NULL',
  'SELECT ''mechanic_latitude already exists; unchanged'''
);
PREPARE ra_stmt FROM @ra_sql; EXECUTE ra_stmt; DEALLOCATE PREPARE ra_stmt;

SET @ra_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'users'
     AND column_name = 'mechanic_longitude') = 0,
  'ALTER TABLE users ADD COLUMN mechanic_longitude DECIMAL(9,6) NULL',
  'SELECT ''mechanic_longitude already exists; unchanged'''
);
PREPARE ra_stmt FROM @ra_sql; EXECUTE ra_stmt; DEALLOCATE PREPARE ra_stmt;

SET @ra_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'assistance_requests'
     AND column_name = 'latitude') = 0,
  'ALTER TABLE assistance_requests ADD COLUMN latitude DECIMAL(9,6) NULL',
  'SELECT ''latitude already exists; unchanged'''
);
PREPARE ra_stmt FROM @ra_sql; EXECUTE ra_stmt; DEALLOCATE PREPARE ra_stmt;

SET @ra_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'assistance_requests'
     AND column_name = 'longitude') = 0,
  'ALTER TABLE assistance_requests ADD COLUMN longitude DECIMAL(9,6) NULL',
  'SELECT ''longitude already exists; unchanged'''
);
PREPARE ra_stmt FROM @ra_sql; EXECUTE ra_stmt; DEALLOCATE PREPARE ra_stmt;

-- Add coordinate range/pair constraints.
ALTER TABLE users
  ADD CONSTRAINT chk_users_mechanic_coordinates
  CHECK (
    (mechanic_latitude IS NULL AND mechanic_longitude IS NULL)
    OR
    (mechanic_latitude IS NOT NULL AND mechanic_longitude IS NOT NULL
     AND mechanic_latitude BETWEEN -90 AND 90
     AND mechanic_longitude BETWEEN -180 AND 180)
  );

ALTER TABLE assistance_requests
  ADD CONSTRAINT chk_assistance_request_coordinates
  CHECK (
    (latitude IS NULL AND longitude IS NULL)
    OR
    (latitude IS NOT NULL AND longitude IS NOT NULL
     AND latitude BETWEEN -90 AND 90
     AND longitude BETWEEN -180 AND 180)
  );
