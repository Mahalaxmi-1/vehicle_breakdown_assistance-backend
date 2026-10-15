-- RoadAssist additive migration: owner-request and mechanic coordinates.
-- MySQL 8.0.16+ is required for enforced CHECK constraints.
-- This script only permits roadassist_test or a suffixed roadassist_test_*
-- schema. The first run must target roadassist_test, never roadassist_db.
-- Confirm SELECT DATABASE() before execution.
-- Existing coordinate columns must be nullable DECIMAL(9,6); existing checks
-- that reference these fields must match the expressions below exactly.
-- Existing rows remain NULL. Text location fields and existing records are preserved.
--
-- MySQL DDL may commit independently; this migration is NOT transactional.
-- If execution stops partway through, inspect all four column definitions and
-- both CHECK expressions before rerunning. Do not drop or rewrite existing objects.
-- Run without the MySQL client's --force option. The preflight assertion fails
-- before ALTER TABLE on any invalid/missing precondition, and every DDL statement
-- is also gated by the preflight result.

SET @roadassist_schema = DATABASE();
SET @roadassist_major = CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED);
SET @roadassist_minor = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(VERSION(), '.', 2), '.', -1) AS UNSIGNED);
SET @roadassist_patch = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(VERSION(), '.', 3), '.', -1) AS UNSIGNED);
SET @roadassist_schema_ok = IF(
    @roadassist_schema = 'roadassist_test'
    OR @roadassist_schema REGEXP '^roadassist_test_[[:alnum:]_]+$', 1, 0);
SET @roadassist_version_ok = IF(
    LOWER(@@version_comment) NOT LIKE '%mariadb%'
    AND (
      @roadassist_major > 8
      OR (@roadassist_major = 8 AND @roadassist_minor > 0)
      OR (@roadassist_major = 8 AND @roadassist_minor = 0 AND @roadassist_patch >= 16)
    ), 1, 0);

-- Fail closed if a metadata statement errors and the client continues. MySQL
-- user variables persist for the connection, so initialize every preflight
-- input before querying information_schema.
SET @roadassist_required_table_count = 0;
SET @roadassist_bad_coordinate_columns = 1;
SET @roadassist_bad_users_checks = 1;
SET @roadassist_bad_request_checks = 1;
SET @roadassist_has_users_check = -1;
SET @roadassist_has_requests_check = -1;
SET @roadassist_checks_ok = 0;
SET @roadassist_preflight_ok = 0;

-- Required target tables must exist as base tables.
SELECT COUNT(*) INTO @roadassist_required_table_count
FROM information_schema.tables
WHERE table_schema = @roadassist_schema
  AND table_type = 'BASE TABLE'
  AND table_name IN ('users', 'assistance_requests');
SET @roadassist_tables_ok = IF(@roadassist_required_table_count = 2, 1, 0);

-- Record the existing coordinate-column definitions. Missing columns are okay;
-- present columns must be nullable DECIMAL(9,6) with no non-NULL default.
SELECT COUNT(*) INTO @roadassist_bad_coordinate_columns
FROM information_schema.columns
WHERE table_schema = @roadassist_schema
  AND (
    (table_name = 'users' AND column_name IN ('mechanic_latitude', 'mechanic_longitude'))
    OR
    (table_name = 'assistance_requests' AND column_name IN ('latitude', 'longitude'))
  )
  AND NOT (
    data_type = 'decimal'
    AND numeric_precision = 9
    AND numeric_scale = 6
    AND is_nullable = 'YES'
    AND column_default IS NULL
  );
SET @roadassist_columns_ok = IF(@roadassist_bad_coordinate_columns = 0, 1, 0);

-- Canonical expressions used to verify existing coordinate CHECK constraints.
-- Whitespace and identifier quoting are normalized. Parentheses are retained
-- because removing them can make different Boolean logic appear equivalent.
SET @roadassist_users_check_expected =
  '((mechanic_latitudeisnullandmechanic_longitudeisnull)or(mechanic_latitudeisnotnullandmechanic_longitudeisnotnullandmechanic_latitudebetween-90and90andmechanic_longitudebetween-180and180))';
SET @roadassist_requests_check_expected =
  '((latitudeisnullandlongitudeisnull)or(latitudeisnotnullandlongitudeisnotnullandlatitudebetween-90and90andlongitudebetween-180and180))';

-- Any existing CHECK that references these fields, or already uses the
-- migration's intended constraint name, must be verifiable and exact. This
-- catches same-named mismatches and partial/suspicious coordinate checks.
-- Unrelated checks, including users.chk_users_availability, are ignored because
-- neither their name nor their expression references coordinate columns.
SELECT COUNT(*) INTO @roadassist_bad_users_checks
FROM information_schema.table_constraints tc
LEFT JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = @roadassist_schema
  AND tc.table_name = 'users'
  AND (
    tc.constraint_name = 'chk_users_mechanic_coordinates'
    OR
    (tc.constraint_type = 'CHECK' AND (
      LOWER(COALESCE(cc.check_clause, '')) LIKE '%mechanic_latitude%'
      OR LOWER(COALESCE(cc.check_clause, '')) LIKE '%mechanic_longitude%'
    ))
  )
  AND (
    tc.constraint_type <> 'CHECK'
    OR
    COALESCE(tc.enforced, 'NO') <> 'YES'
    OR
    cc.constraint_name IS NULL
    OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause,
      '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''))
      <> @roadassist_users_check_expected
  );

SELECT COUNT(*) INTO @roadassist_bad_request_checks
FROM information_schema.table_constraints tc
LEFT JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = @roadassist_schema
  AND tc.table_name = 'assistance_requests'
  AND (
    tc.constraint_name = 'chk_assistance_request_coordinates'
    OR
    (tc.constraint_type = 'CHECK' AND (
      LOWER(COALESCE(cc.check_clause, '')) LIKE '%latitude%'
      OR LOWER(COALESCE(cc.check_clause, '')) LIKE '%longitude%'
    ))
  )
  AND (
    tc.constraint_type <> 'CHECK'
    OR
    COALESCE(tc.enforced, 'NO') <> 'YES'
    OR
    cc.constraint_name IS NULL
    OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause,
      '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''))
      <> @roadassist_requests_check_expected
  );
SET @roadassist_checks_ok = IF(
    @roadassist_bad_users_checks = 0 AND @roadassist_bad_request_checks = 0, 1, 0);

-- Diagnostic rows are empty when all present objects are compatible.
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = @roadassist_schema
  AND (
    (table_name = 'users' AND column_name IN ('mechanic_latitude', 'mechanic_longitude'))
    OR
    (table_name = 'assistance_requests' AND column_name IN ('latitude', 'longitude'))
  )
  AND NOT (
    data_type = 'decimal'
    AND numeric_precision = 9
    AND numeric_scale = 6
    AND is_nullable = 'YES'
    AND column_default IS NULL
  );

SELECT tc.table_name, tc.constraint_name, tc.constraint_type, cc.check_clause
FROM information_schema.table_constraints tc
LEFT JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = @roadassist_schema
  AND (
    (tc.table_name = 'users' AND (
      tc.constraint_name = 'chk_users_mechanic_coordinates'
      OR (tc.constraint_type = 'CHECK' AND (
        LOWER(COALESCE(cc.check_clause, '')) LIKE '%mechanic_latitude%'
        OR LOWER(COALESCE(cc.check_clause, '')) LIKE '%mechanic_longitude%'
      ))
    ))
    OR
    (tc.table_name = 'assistance_requests' AND (
      tc.constraint_name = 'chk_assistance_request_coordinates'
      OR (tc.constraint_type = 'CHECK' AND (
        LOWER(COALESCE(cc.check_clause, '')) LIKE '%latitude%'
        OR LOWER(COALESCE(cc.check_clause, '')) LIKE '%longitude%'
      ))
    ))
  )
  AND (
    tc.constraint_type <> 'CHECK'
    OR cc.constraint_name IS NULL
    OR COALESCE(tc.enforced, 'NO') <> 'YES'
    OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause,
      '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''))
      <> IF(tc.table_name = 'users', @roadassist_users_check_expected,
        @roadassist_requests_check_expected)
  );

-- These counts determine whether an equivalent valid CHECK already exists,
-- including one created under a different name. Invalid coordinate-related
-- checks were rejected above and are never silently skipped.
SELECT COUNT(*) INTO @roadassist_has_users_check
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = @roadassist_schema
  AND tc.table_name = 'users'
  AND tc.constraint_type = 'CHECK'
  AND tc.enforced = 'YES'
  AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause,
    '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''))
    = @roadassist_users_check_expected;

SELECT COUNT(*) INTO @roadassist_has_requests_check
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = @roadassist_schema
  AND tc.table_name = 'assistance_requests'
  AND tc.constraint_type = 'CHECK'
  AND tc.enforced = 'YES'
  AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause,
    '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''))
    = @roadassist_requests_check_expected;

SET @roadassist_preflight_ok = IF(
    @roadassist_schema_ok = 1
    AND @roadassist_version_ok = 1
    AND @roadassist_tables_ok = 1
    AND @roadassist_columns_ok = 1
    AND @roadassist_checks_ok = 1
    AND @roadassist_has_users_check >= 0
    AND @roadassist_has_requests_check >= 0, 1, 0);

SELECT
  @roadassist_schema AS selected_schema,
  VERSION() AS server_version,
  @@version_comment AS server_product,
  @roadassist_schema_ok AS allowed_schema,
  @roadassist_version_ok AS check_constraints_supported,
  @roadassist_required_table_count AS required_tables_found,
  @roadassist_bad_coordinate_columns AS mismatched_existing_coordinate_columns,
  @roadassist_bad_users_checks AS mismatched_user_coordinate_checks,
  @roadassist_bad_request_checks AS mismatched_request_coordinate_checks,
  @roadassist_preflight_ok AS preflight_passed;

-- A failed assertion stops normal batch execution before any ALTER. If a client
-- is configured to continue after SQL errors, the DDL gates below still prevent
-- every schema change while @roadassist_preflight_ok is 0.
DROP TEMPORARY TABLE IF EXISTS roadassist_v6_preflight_assertion;
CREATE TEMPORARY TABLE roadassist_v6_preflight_assertion (
  preflight_passed TINYINT NOT NULL
);
INSERT INTO roadassist_v6_preflight_assertion (preflight_passed)
VALUES (IF(@roadassist_preflight_ok = 1, 1, NULL));
DROP TEMPORARY TABLE roadassist_v6_preflight_assertion;

-- Add only absent columns. Existing definitions are preserved after preflight.
-- Initialize existence flags to unknown; a failed metadata query must never
-- default to "absent" and trigger an ALTER if the client continues.
SET @roadassist_has_mechanic_latitude = -1;
SET @roadassist_has_mechanic_longitude = -1;
SET @roadassist_has_request_latitude = -1;
SET @roadassist_has_request_longitude = -1;

SELECT COUNT(*) INTO @roadassist_has_mechanic_latitude
FROM information_schema.columns
WHERE table_schema = @roadassist_schema AND table_name = 'users'
  AND column_name = 'mechanic_latitude';
SET @roadassist_sql = IF(@roadassist_preflight_ok = 1 AND @roadassist_has_mechanic_latitude = 0,
  'ALTER TABLE users ADD COLUMN mechanic_latitude DECIMAL(9,6) NULL',
  IF(@roadassist_preflight_ok = 0,
    'SELECT ''ABORTED: preflight failed; no migration ALTER was executed''',
    IF(@roadassist_has_mechanic_latitude = 1,
      'SELECT ''users.mechanic_latitude already exists and was verified; no change''',
      'SELECT ''ABORTED: could not verify users.mechanic_latitude; no change''')));
PREPARE roadassist_stmt FROM @roadassist_sql; EXECUTE roadassist_stmt; DEALLOCATE PREPARE roadassist_stmt;

SELECT COUNT(*) INTO @roadassist_has_mechanic_longitude
FROM information_schema.columns
WHERE table_schema = @roadassist_schema AND table_name = 'users'
  AND column_name = 'mechanic_longitude';
SET @roadassist_sql = IF(@roadassist_preflight_ok = 1 AND @roadassist_has_mechanic_longitude = 0,
  'ALTER TABLE users ADD COLUMN mechanic_longitude DECIMAL(9,6) NULL',
  IF(@roadassist_preflight_ok = 0,
    'SELECT ''ABORTED: preflight failed; no migration ALTER was executed''',
    IF(@roadassist_has_mechanic_longitude = 1,
      'SELECT ''users.mechanic_longitude already exists and was verified; no change''',
      'SELECT ''ABORTED: could not verify users.mechanic_longitude; no change''')));
PREPARE roadassist_stmt FROM @roadassist_sql; EXECUTE roadassist_stmt; DEALLOCATE PREPARE roadassist_stmt;

SELECT COUNT(*) INTO @roadassist_has_request_latitude
FROM information_schema.columns
WHERE table_schema = @roadassist_schema AND table_name = 'assistance_requests'
  AND column_name = 'latitude';
SET @roadassist_sql = IF(@roadassist_preflight_ok = 1 AND @roadassist_has_request_latitude = 0,
  'ALTER TABLE assistance_requests ADD COLUMN latitude DECIMAL(9,6) NULL',
  IF(@roadassist_preflight_ok = 0,
    'SELECT ''ABORTED: preflight failed; no migration ALTER was executed''',
    IF(@roadassist_has_request_latitude = 1,
      'SELECT ''assistance_requests.latitude already exists and was verified; no change''',
      'SELECT ''ABORTED: could not verify assistance_requests.latitude; no change''')));
PREPARE roadassist_stmt FROM @roadassist_sql; EXECUTE roadassist_stmt; DEALLOCATE PREPARE roadassist_stmt;

SELECT COUNT(*) INTO @roadassist_has_request_longitude
FROM information_schema.columns
WHERE table_schema = @roadassist_schema AND table_name = 'assistance_requests'
  AND column_name = 'longitude';
SET @roadassist_sql = IF(@roadassist_preflight_ok = 1 AND @roadassist_has_request_longitude = 0,
  'ALTER TABLE assistance_requests ADD COLUMN longitude DECIMAL(9,6) NULL',
  IF(@roadassist_preflight_ok = 0,
    'SELECT ''ABORTED: preflight failed; no migration ALTER was executed''',
    IF(@roadassist_has_request_longitude = 1,
      'SELECT ''assistance_requests.longitude already exists and was verified; no change''',
      'SELECT ''ABORTED: could not verify assistance_requests.longitude; no change''')));
PREPARE roadassist_stmt FROM @roadassist_sql; EXECUTE roadassist_stmt; DEALLOCATE PREPARE roadassist_stmt;

-- Add a range/pair CHECK only when no equivalent verified expression exists.
SET @roadassist_sql = IF(@roadassist_preflight_ok = 1 AND @roadassist_has_users_check = 0,
  'ALTER TABLE users ADD CONSTRAINT chk_users_mechanic_coordinates CHECK ((mechanic_latitude IS NULL AND mechanic_longitude IS NULL) OR (mechanic_latitude IS NOT NULL AND mechanic_longitude IS NOT NULL AND mechanic_latitude BETWEEN -90 AND 90 AND mechanic_longitude BETWEEN -180 AND 180))',
  IF(@roadassist_preflight_ok = 0,
    'SELECT ''ABORTED: preflight failed; no migration ALTER was executed''',
    'SELECT ''an equivalent user coordinate CHECK exists and was verified; no change'''));
PREPARE roadassist_stmt FROM @roadassist_sql; EXECUTE roadassist_stmt; DEALLOCATE PREPARE roadassist_stmt;

SET @roadassist_sql = IF(@roadassist_preflight_ok = 1 AND @roadassist_has_requests_check = 0,
  'ALTER TABLE assistance_requests ADD CONSTRAINT chk_assistance_request_coordinates CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))',
  IF(@roadassist_preflight_ok = 0,
    'SELECT ''ABORTED: preflight failed; no migration ALTER was executed''',
    'SELECT ''an equivalent request coordinate CHECK exists and was verified; no change'''));
PREPARE roadassist_stmt FROM @roadassist_sql; EXECUTE roadassist_stmt; DEALLOCATE PREPARE roadassist_stmt;

-- Postflight: verify resulting column definitions and coordinate CHECK expressions.
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = @roadassist_schema
  AND (
    (table_name = 'users' AND column_name IN ('mechanic_latitude', 'mechanic_longitude'))
    OR
    (table_name = 'assistance_requests' AND column_name IN ('latitude', 'longitude'))
  )
ORDER BY table_name, column_name;

SELECT tc.table_name, tc.constraint_name, cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = @roadassist_schema
  AND tc.table_name IN ('users', 'assistance_requests')
  AND tc.constraint_type = 'CHECK'
  AND (
    LOWER(COALESCE(cc.check_clause, '')) LIKE '%mechanic_latitude%'
    OR LOWER(COALESCE(cc.check_clause, '')) LIKE '%mechanic_longitude%'
    OR LOWER(COALESCE(cc.check_clause, '')) LIKE '%latitude%'
    OR LOWER(COALESCE(cc.check_clause, '')) LIKE '%longitude%'
  )
ORDER BY tc.table_name, tc.constraint_name;
