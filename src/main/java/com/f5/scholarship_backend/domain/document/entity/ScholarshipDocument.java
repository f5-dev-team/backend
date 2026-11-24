package com.f5.scholarship_backend.domain.document.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;
import com.f5.scholarship_backend.domain.document.model.StoredAttachment;

@Entity
@Table(name = "scholarship_documents", uniqueConstraints = @UniqueConstraint(columnNames = {"scholarship_id", "sha256"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScholarshipDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "document_id") private Long id;
    @Column(name = "scholarship_id", nullable = false) private Long scholarshipId;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "file_type", nullable = false, length = 8) private String fileType;
    @Column(name = "s3_bucket", nullable = false, length = 63) private String bucket;
    @Column(name = "s3_key", nullable = false, length = 1024) private String key;
    @Column(name = "s3_version_id", length = 1024) private String versionId;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "byte_size", nullable = false) private long byteSize;
    @Column(name = "source_file_url", nullable = false, length = 2048) private String sourceFileUrl;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    /** 기존 문서는 갱신하지 않고 처음 확인한 불변 파일 메타데이터를 보존한다. */
    public ScholarshipDocument(Long scholarshipId, StoredAttachment file, Instant now) {
        this.scholarshipId = scholarshipId; fileName = file.name(); fileType = file.type().name();
        bucket = file.bucket(); key = file.key(); versionId = file.versionId(); sha256 = file.sha256();
        byteSize = file.byteSize(); sourceFileUrl = file.sourceUrl().toString(); createdAt = now;
    }
}
