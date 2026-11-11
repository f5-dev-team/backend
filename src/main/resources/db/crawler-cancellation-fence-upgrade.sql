-- crawler-schema-upgrade.sql 적용 후 한 번 실행한다.
-- 신규 설치도 기본 스키마 -> crawler-schema-upgrade -> 이 파일 순으로 적용한다.
-- 기존 실행 이력·임대·공고 데이터는 삭제하지 않는다.
ALTER TABLE crawler_leases
  ADD COLUMN cancelled BOOLEAN NOT NULL DEFAULT FALSE;