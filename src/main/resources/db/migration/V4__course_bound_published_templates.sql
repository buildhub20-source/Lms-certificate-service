-- Binds a certificate template to a course and gives it a draft/published
-- lifecycle.
--
-- Until now a template was chosen by organisation default alone, so every
-- course in a tenant produced the same certificate and a template was live the
-- moment it was saved. Issuing now looks for a template published for the
-- course, falling back to the organisation default, so existing templates keep
-- working as the fallback rather than being stranded.
ALTER TABLE certificate_templates
    ADD COLUMN IF NOT EXISTS course_id    UUID,
    ADD COLUMN IF NOT EXISTS published    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

-- Templates that predate this migration were live by virtue of existing, so
-- they stay live. Marking them draft would silently stop certificates issuing.
UPDATE certificate_templates
   SET published = TRUE,
       published_at = COALESCE(published_at, created_at)
 WHERE published = FALSE;

-- The lookup on the issuing path: a published template for this course.
CREATE INDEX IF NOT EXISTS idx_certificate_templates_course_published
    ON certificate_templates(organization_id, course_id, published);

-- At most one published template per course, per organisation. A second one
-- would make which certificate a learner receives a matter of row order.
CREATE UNIQUE INDEX IF NOT EXISTS uq_certificate_template_published_course
    ON certificate_templates(organization_id, course_id)
 WHERE published = TRUE AND course_id IS NOT NULL;

-- A certificate earned before its course had a template is held rather than
-- issued empty, and is completed once a template is published.
COMMENT ON COLUMN certificate_templates.course_id IS
    'Course this template is published for; NULL means it serves as the organisation fallback.';
