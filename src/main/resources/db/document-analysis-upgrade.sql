-- MySQL 8.4. Apply once after the existing scholarship, version and document tables exist.
ALTER TABLE scholarships
  ADD COLUMN document_analysis_id VARCHAR(128) NULL,
  ADD COLUMN document_analysis_version INT UNSIGNED NULL,
  ADD COLUMN document_analysis_next_at DATETIME(6) NULL,
  ADD KEY ix_scholarship_document_analysis (processing_status, document_analysis_next_at, scholarship_id);

ALTER TABLE scholarship_versions
  ADD COLUMN requirements_complete TINYINT UNSIGNED NULL,
  ADD CONSTRAINT ck_version_requirements_complete CHECK (requirements_complete IN (0, 1));

ALTER TABLE scholarship_conditions
  ADD COLUMN gpa_scale DECIMAL(4,2) NULL,
  ADD COLUMN source_document_id BIGINT UNSIGNED NULL,
  ADD COLUMN source_page INT UNSIGNED NULL,
  ADD COLUMN source_section VARCHAR(255) NULL,
  ADD CONSTRAINT ck_condition_gpa_scale CHECK (gpa_scale IS NULL OR gpa_scale > 0),
  ADD CONSTRAINT ck_condition_source_page CHECK (source_page IS NULL OR (source_page >= 1 AND source_document_id IS NOT NULL)),
  ADD CONSTRAINT fk_condition_source_document FOREIGN KEY (scholarship_id, version, source_document_id)
    REFERENCES version_documents (scholarship_id, version, document_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
