package com.f5.scholarship_backend.domain.document.service;

import java.time.ZoneOffset;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.dto.DocumentAccessResponse;
import com.f5.scholarship_backend.domain.document.repository.ScholarshipDocumentRepository;
import com.f5.scholarship_backend.domain.document.storage.OriginalDocumentAccess;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class DocumentAccessService {
	private final ScholarshipDocumentRepository documents;
	private final OriginalDocumentAccess access;

	public DocumentAccessService(ScholarshipDocumentRepository documents, OriginalDocumentAccess access) {
		this.documents = documents;
		this.access = access;
	}

	public DocumentAccessResponse findAccess(String scholarshipId, String documentId) {
		var target = documents.findAccessTarget(requireId(scholarshipId), requireId(documentId))
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		var document = target.getDocument();

		try {
			var signed = access.issue(document.getBucket(), document.getKey(), document.getVersionId());
			return new DocumentAccessResponse(document.getId().toString(), document.getFileName(),
					document.getFileType(), target.getSourceUrl(), signed.url(), signed.expiresAt().atOffset(ZoneOffset.UTC));
		} catch (DocumentException unavailable) {
			throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
		}
	}

	private static long requireId(String value) {
		try {
			long id = Long.parseLong(value);
			if (id > 0) {
				return id;
			}
		} catch (NumberFormatException invalidId) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}
		throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
	}
}
