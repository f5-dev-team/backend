package com.f5.scholarship_backend.domain.document.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "version_documents")
@IdClass(VersionDocument.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VersionDocument {
    @Id @Column(name = "scholarship_id") private Long scholarshipId;
    @Id @Column(name = "version") private int version;
    @Id @Column(name = "document_id") private Long documentId;
    public VersionDocument(Long scholarshipId, int version, Long documentId) {
        this.scholarshipId = scholarshipId; this.version = version; this.documentId = documentId;
    }
    public record Key(Long scholarshipId, int version, Long documentId) implements Serializable { }
}
