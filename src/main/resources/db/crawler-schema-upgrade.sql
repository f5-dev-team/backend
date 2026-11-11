-- MySQL 8.4 기준. 기존 schema.sql 적용 이후 한 번 실행하는 #11 추가 스키마.
-- 자동 실행하지 않는다. 운영에서 crawler를 활성화하기 전에 검토·적용한다.
-- 기존 버전의 출처를 확신할 수 없으면 UNKNOWN으로 보존하여 자동 덮어쓰기를 막는다.
ALTER TABLE scholarship_versions
  ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
  ADD COLUMN source_attachments LONGTEXT NULL;
UPDATE scholarship_versions SET origin = 'ADMIN' WHERE changed_by_user_id IS NOT NULL;

CREATE TABLE scholarship_crawl_state (
  scholarship_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  last_source_fingerprint CHAR(64) NULL,
  last_imported_version INT UNSIGNED NOT NULL DEFAULT 0,
  candidate_payload LONGTEXT NULL,
  candidate_fingerprint CHAR(64) NULL,
  requires_review BOOLEAN NOT NULL DEFAULT FALSE,
  observed_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_crawl_state_scholarship FOREIGN KEY (scholarship_id)
    REFERENCES scholarships(scholarship_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE crawler_leases (
  name VARCHAR(64) NOT NULL PRIMARY KEY,
  owner VARCHAR(64) NULL,
  locked_until DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;