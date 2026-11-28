package com.f5.scholarship_backend.domain.document.repository;

import java.util.List;
import com.f5.scholarship_backend.domain.document.entity.VersionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VersionDocumentRepository extends JpaRepository<VersionDocument, VersionDocument.Key> {
	@Query("""
			select d.id as documentId, d.fileName as fileName, d.fileType as fileType
			from VersionDocument link join ScholarshipDocument d
			    on d.id = link.documentId and d.scholarshipId = link.scholarshipId
			where link.scholarshipId = :scholarshipId and link.version = :version
			order by d.id
			""")
	List<DocumentMetadata> findDocumentMetadata(long scholarshipId, int version);

	@Query("""
			select d.id as documentId, d.bucket as bucket, d.key as objectKey
			from VersionDocument link join ScholarshipDocument d
			    on d.id = link.documentId and d.scholarshipId = link.scholarshipId
			where link.scholarshipId = :scholarshipId and link.version = :version
			order by d.id
			""")
	List<DocumentAnalysisSource> findDocumentAnalysisSources(long scholarshipId, int version);

	interface DocumentAnalysisSource {
		Long getDocumentId();
		String getBucket();
		String getObjectKey();
	}

	interface DocumentMetadata {
		Long getDocumentId();
		String getFileName();
		String getFileType();
	}
}
