ALTER TABLE certificate_templates
    ADD COLUMN IF NOT EXISTS name VARCHAR(160) NOT NULL DEFAULT 'Certificate template',
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'LMS_RENDERED',
    ADD COLUMN IF NOT EXISTS pdf_template_url VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS field_layout TEXT;

DO $$ BEGIN
    ALTER TABLE certificate_templates
        ADD CONSTRAINT chk_certificate_template_source
        CHECK (source_type IN ('LMS_RENDERED', 'INSTITUTION_PDF'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
