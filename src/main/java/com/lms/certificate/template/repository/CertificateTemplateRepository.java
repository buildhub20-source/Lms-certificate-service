package com.lms.certificate.template.repository;

import com.lms.certificate.template.entity.CertificateTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, UUID> {

    /** Returns the active default for one tenant; each tenant may own several templates. */
    Optional<CertificateTemplate> findByOrganizationIdAndDefaultTemplateTrue(UUID organizationId);

    /** Returns the global default template. */
    Optional<CertificateTemplate> findByDefaultTemplateTrue();

    // --- Resolution order used when issuing; see TemplateResolver. ---

    Optional<CertificateTemplate> findFirstByOrganizationIdAndCourseIdAndPublishedTrue(
            UUID organizationId, UUID courseId);

    Optional<CertificateTemplate> findFirstByCourseIdAndPublishedTrue(UUID courseId);

    Optional<CertificateTemplate> findFirstByOrganizationIdAndCourseIdIsNullAndPublishedTrueAndDefaultTemplateTrue(
            UUID organizationId);

    Optional<CertificateTemplate> findFirstByCourseIdIsNullAndPublishedTrueAndDefaultTemplateTrue();

    /**
     * Any other published template already bound to this course. Used to retire
     * the previous one when a new template is published, so the unique index
     * never has to reject the publish.
     */
    List<CertificateTemplate> findByOrganizationIdAndCourseIdAndPublishedTrue(UUID organizationId, UUID courseId);

    List<CertificateTemplate> findByCourseId(UUID courseId);
}
