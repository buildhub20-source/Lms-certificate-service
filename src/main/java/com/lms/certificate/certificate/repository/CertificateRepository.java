package com.lms.certificate.certificate.repository;

import com.lms.certificate.certificate.entity.Certificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface CertificateRepository extends JpaRepository<Certificate, UUID> {
    Optional<Certificate> findBySerialNumber(String serialNumber);
    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);
    Page<Certificate> findByStudentId(UUID studentId, Pageable pageable);
    Optional<Certificate> findByIdAndStudentId(UUID id, UUID studentId);
    Page<Certificate> findByCourseId(UUID courseId, Pageable pageable);
    /** All ACTIVE certs for a course — used by bulk-issue to skip already-issued. */
    List<Certificate> findByStudentIdIn(List<UUID> studentIds);
}