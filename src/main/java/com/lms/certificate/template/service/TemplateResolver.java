package com.lms.certificate.template.service;

import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.repository.CertificateTemplateRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Decides which template a certificate is rendered from.
 *
 * <p>The order is narrowest first: a template published for the course, then
 * the organisation's published fallback, then a global one. The fallback steps
 * exist because templates predate course binding — without them every course
 * that relied on the organisation default would stop issuing the moment this
 * was introduced.
 *
 * <p>Drafts are never resolved. A template is previewable while it is being
 * set up, and only becomes capable of issuing when it is published.
 */
@Component
public class TemplateResolver {

    private final CertificateTemplateRepository templates;

    public TemplateResolver(CertificateTemplateRepository templates) {
        this.templates = templates;
    }

    /**
     * The template that should be used for this course, or empty when the
     * organisation has not published one — which is what holds a certificate
     * as pending rather than issuing it without a PDF.
     */
    public Optional<CertificateTemplate> resolve(UUID organizationId, UUID courseId) {
        if (courseId != null) {
            Optional<CertificateTemplate> forCourse = organizationId == null
                    ? templates.findFirstByCourseIdAndPublishedTrue(courseId)
                    : templates.findFirstByOrganizationIdAndCourseIdAndPublishedTrue(organizationId, courseId);
            if (forCourse.isPresent()) {
                return forCourse;
            }
        }
        if (organizationId != null) {
            Optional<CertificateTemplate> orgFallback =
                    templates.findFirstByOrganizationIdAndCourseIdIsNullAndPublishedTrueAndDefaultTemplateTrue(
                            organizationId);
            if (orgFallback.isPresent()) {
                return orgFallback;
            }
        }
        return templates.findFirstByCourseIdIsNullAndPublishedTrueAndDefaultTemplateTrue();
    }
}
