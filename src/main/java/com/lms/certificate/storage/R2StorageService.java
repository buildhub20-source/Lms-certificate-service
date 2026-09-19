package com.lms.certificate.storage;

import com.lms.certificate.config.R2Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/**
 * Stores and retrieves files from Cloudflare R2 (S3-compatible).
 */
@Service
public class R2StorageService {

    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);

    private final S3Client s3Client;
    private final R2Config r2Config;

    public R2StorageService(S3Client s3Client, R2Config r2Config) {
        this.s3Client = s3Client;
        this.r2Config = r2Config;
    }

    /**
     * Uploads a PDF byte array to R2 and returns its public URL.
     *
     * @param content      the PDF bytes
     * @param serialNumber used to build a deterministic, human-readable key
     * @return the public URL of the uploaded file
     */
    public String uploadCertificatePdf(byte[] content, UUID tenantId, String serialNumber) {
        String key = tenantPrefix(tenantId) + "/pdfs/" + serialNumber + ".pdf";
        return upload(content, key, "application/pdf");
    }

    /**
     * Uploads an image (template background or signature) and returns its public URL.
     *
     * @param content     raw image bytes
     * @param contentType MIME type (e.g. "image/png")
     * @return the public URL
     */
    public String uploadTemplateImage(byte[] content, UUID tenantId, String contentType) {
        String key = tenantPrefix(tenantId) + "/templates/" + UUID.randomUUID() + "." + extension(contentType);
        return upload(content, key, contentType);
    }

    /** Stores the original, reusable institution PDF separately from issued PDFs. */
    public String uploadInstitutionTemplatePdf(byte[] content, UUID tenantId, UUID templateId) {
        String key = tenantPrefix(tenantId) + "/templates/" + templateId + "/original.pdf";
        return upload(content, key, "application/pdf");
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private String upload(byte[] content, String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Config.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));

        String url = r2Config.getPublicUrl() + "/" + key;
        log.info("Uploaded {} bytes to R2: {}", content.length, url);
        return url;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png"  -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default           -> "bin";
        };
    }

    private String tenantPrefix(UUID tenantId) {
        return "tenants/" + (tenantId == null ? "shared" : tenantId) + "/certificates";
    }
}
