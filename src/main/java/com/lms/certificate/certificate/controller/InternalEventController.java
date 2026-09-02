package com.lms.certificate.certificate.controller;

import com.lms.certificate.certificate.service.CertificateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Internal webhook endpoint — receives enrollment-completed events from LMS-BackEnd.
 * Protected by {@link com.lms.certificate.security.ServiceKeyAuthFilter}.
 */
@RestController
@RequestMapping("/api/v1/internal/events")
public class InternalEventController {

    private static final Logger log = LoggerFactory.getLogger(InternalEventController.class);

    private final CertificateService certificateService;

    public InternalEventController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping("/enrollment-completed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> onEnrollmentCompleted(@RequestBody Map<String, String> payload) {
        UUID studentId = UUID.fromString(payload.get("studentId"));
        UUID courseId  = UUID.fromString(payload.get("courseId"));
        log.info("Enrollment-completed webhook: student={} course={}", studentId, courseId);
        certificateService.handleEnrollmentCompleted(studentId, courseId);
        return Map.of("status", "accepted");
    }
}