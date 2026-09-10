# Institution PDF certificate templates

Tenant administrators and instructors can upload an institution-owned certificate PDF and have the certificate service produce a new, personalised copy when a learner completes a course.

## Upload

`POST /api/v1/admin/certificate-templates/institution-pdf`

The request is `multipart/form-data`:

- `file`: the reusable source PDF (`application/pdf`, maximum 15 MB)
- `template`: a JSON part containing `organizationId`, `name`, `isDefault`, and `fieldLayout`

The original file is stored at `Certificates/<tenant-id>/templates/<template-id>/original.pdf`. Issued copies are stored independently at `Certificates/<tenant-id>/pdfs/<certificate-serial>.pdf`; issuing a certificate never changes the institution's original.

## Dynamic fields

`fieldLayout` is a JSON object. Coordinates are PDF points from the lower-left corner. Page numbering begins at 1.

```json
{
  "learnerFullName": { "page": 1, "x": 421, "y": 330, "fontSize": 24, "align": "CENTER" },
  "courseTitle":     { "page": 1, "x": 421, "y": 270, "fontSize": 16, "align": "CENTER" },
  "issuedDate":      { "page": 1, "x": 145, "y": 100, "fontSize": 10, "align": "LEFT" },
  "certificateNumber": { "page": 1, "x": 697, "y": 100, "fontSize": 8, "align": "RIGHT" }
}
```

Supported fields are `learnerFullName`, `courseTitle`, `organizationName`, `issuedDate`, and `certificateNumber`. If no layout is supplied, the learner name is placed in the middle of the first page as a safe starting default.

## Issuance

The current `enrollment-completed` event already triggers certificate generation. When the tenant's default template is an `INSTITUTION_PDF` template, the service stamps the completion data into that PDF and publishes the resulting learner-specific certificate. LMS-rendered templates continue to work unchanged.

Assessment-triggered issuance requires a distinct assessment completion event and a certificate-to-assessment relationship; it is intentionally not inferred from a course completion event.
