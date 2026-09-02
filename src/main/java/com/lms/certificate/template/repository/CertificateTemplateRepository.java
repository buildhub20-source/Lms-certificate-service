package com.lms.certificate.template.repository;

import com.lms.certificate.template.entity.CertificateTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, UUID> {
    /** Returns the org-specific template if one exists. */
    Optional<CertificateTemplate> findByOrganizationId(UUID organizationId);
    /** Returns the global default template. */
    Optional<CertificateTemplate> findByDefaultTemplateTrue();
}