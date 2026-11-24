package com.f5.scholarship_backend.domain.document.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.f5.scholarship_backend.domain.document.entity.ScholarshipDocument;

public interface ScholarshipDocumentRepository extends JpaRepository<ScholarshipDocument, Long> {
    Optional<ScholarshipDocument> findByScholarshipIdAndSha256(Long scholarshipId, String sha256);
}
