-- Apply once to an existing MySQL database before deploying the student profile API.
-- Keep old columns so age, address text, and unqualified income data are not guessed or discarded.
ALTER TABLE student_profiles
    ADD COLUMN department_code VARCHAR(100) NULL,
    ADD COLUMN income_status VARCHAR(20) NULL,
    ADD COLUMN income_year INT NULL,
    ADD COLUMN income_semester INT NULL,
    ADD COLUMN income_scale VARCHAR(20) NULL,
    ADD COLUMN income_bracket_code VARCHAR(2) NULL,
    ADD COLUMN special_eligibility VARCHAR(20) NULL,
    ADD COLUMN volunteer_minutes BIGINT NULL,
    ADD COLUMN birth_date DATE NULL,
    ADD COLUMN residence_status VARCHAR(20) NULL,
    ADD COLUMN sido_code VARCHAR(2) NULL,
    ADD COLUMN sigungu_code VARCHAR(5) NULL;
