-- Make certificate issuance safe and fast for tenant-scoped workloads.
-- The unique constraint also supplies the student/course duplicate-lookup index.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_cert_student_course'
          AND conrelid = 'certificates'::regclass
    ) THEN
        ALTER TABLE certificates
            ADD CONSTRAINT uq_cert_student_course UNIQUE (student_id, course_id);
    END IF;
END $$;

-- Queries scoped to an organisation generally sort newest certificates first.
CREATE INDEX IF NOT EXISTS idx_certificates_organization_id
    ON certificates (organization_id);

CREATE INDEX IF NOT EXISTS idx_certificates_organization_issued_at
    ON certificates (organization_id, issued_at DESC);
