package com.lms.certificate.certificate.entity;

/** Lifecycle state of a certificate. */
public enum CertificateStatus {
    /** Certificate is valid and active. */
    ACTIVE,
    /** Certificate has been revoked by an admin. */
    REVOKED
}