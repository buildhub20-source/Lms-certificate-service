package com.lms.certificate.certificate.entity;

/** Lifecycle state of a certificate. */
public enum CertificateStatus {
    /**
     * Earned, but the course had no published template when it was issued, so
     * there is no PDF yet. Publishing a template for the course completes it.
     */
    PENDING,
    /** Certificate is valid and active. */
    ACTIVE,
    /** Certificate has been revoked by an admin. */
    REVOKED
}