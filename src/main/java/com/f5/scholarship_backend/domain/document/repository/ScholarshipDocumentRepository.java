package com.f5.scholarship_backend.domain.document.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.f5.scholarship_backend.domain.document.entity.ScholarshipDocument;

public interface ScholarshipDocumentRepository extends JpaRepository<ScholarshipDocument, Long> {
    Optional<ScholarshipDocument> findByScholarshipIdAndSha256(Long scholarshipId, String sha256);

    @Query("""
            select document as document, version.sourceUrl as sourceUrl
            from ScholarshipDocument document
            join VersionDocument link on link.documentId = document.id and link.scholarshipId = document.scholarshipId
            join ScholarshipVersion version on version.scholarshipId = link.scholarshipId and version.version = link.version
            where document.scholarshipId = :scholarshipId and document.id = :documentId
                and link.version = (
                    select max(other.version) from VersionDocument other
                    where other.scholarshipId = document.scholarshipId and other.documentId = document.id
                )
            """)
    Optional<AccessTarget> findAccessTarget(long scholarshipId, long documentId);

    interface AccessTarget {
        ScholarshipDocument getDocument();
        String getSourceUrl();
    }
}
