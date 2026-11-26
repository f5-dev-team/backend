-- MySQL 8.4 기준. 기존 schema.sql 적용 이후 한 번 실행하는 users 추가 스키마.
-- 자동 실행하지 않는다. 기존 사용자 데이터가 있는 경우 아래 두 번째 ALTER 실행 전
-- nickname과 실제 약관 동의 시각(terms_agreed_at)을 사용자별로 백필해야 한다.
-- 동의 시각을 현재 시각으로 일괄 생성하는 등 동의 사실을 추정해 채우지 않는다.
ALTER TABLE users
  ADD COLUMN nickname VARCHAR(30) NULL,
  ADD COLUMN terms_agreed_at DATETIME(6) NULL,
  ADD COLUMN notification_agreed TINYINT UNSIGNED NOT NULL DEFAULT 0;

-- nickname 및 terms_agreed_at을 기존 행에 백필한 뒤 실행한다.
ALTER TABLE users
  MODIFY COLUMN nickname VARCHAR(30) NOT NULL,
  MODIFY COLUMN terms_agreed_at DATETIME(6) NOT NULL,
  ADD CONSTRAINT chk_users_notification_agreed CHECK (notification_agreed IN (0, 1));
