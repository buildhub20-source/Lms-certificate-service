-- Certificates are no longer stored as files. Every download renders the PDF
-- again in memory, so what is printed on it has to be kept here instead:
-- otherwise renaming a course or editing a template would quietly change
-- certificates that were issued long ago.
ALTER TABLE certificates
    ADD COLUMN IF NOT EXISTS verification_token VARCHAR(64),
    ADD COLUMN IF NOT EXISTS recipient_name     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS course_title       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS issuer_name        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS template_id        UUID,
    ADD COLUMN IF NOT EXISTS template_snapshot  TEXT;

-- The QR code links to a token rather than the serial number: serials are
-- printed in plain sight and carry little randomness, so they are easy to
-- guess. Two random UUIDs give 244 random bits.
UPDATE certificates
   SET verification_token = replace(gen_random_uuid()::text, '-', '')
                         || replace(gen_random_uuid()::text, '-', '')
 WHERE verification_token IS NULL;

ALTER TABLE certificates ALTER COLUMN verification_token SET NOT NULL;

DO $$ BEGIN
    ALTER TABLE certificates
        ADD CONSTRAINT uk_cert_verification_token UNIQUE (verification_token);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Existing rows have no snapshot. They are filled in from the LMS the first
-- time each one is downloaded, and stay fixed from then on.
COMMENT ON COLUMN certificates.template_snapshot IS
    'Template settings in force when the certificate was issued, as JSON. NULL only for certificates issued before V5.';
COMMENT ON COLUMN certificates.pdf_url IS
    'Deprecated: generated PDFs are no longer stored. Kept for certificates issued before V5.';
