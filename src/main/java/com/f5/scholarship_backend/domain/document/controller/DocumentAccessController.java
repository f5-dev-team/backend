package com.f5.scholarship_backend.domain.document.controller;

import com.f5.scholarship_backend.domain.document.controller.docs.DocumentAccessControllerDocs;
import com.f5.scholarship_backend.domain.document.dto.DocumentAccessResponse;
import com.f5.scholarship_backend.domain.document.service.DocumentAccessService;
import com.f5.scholarship_backend.global.config.RequestIdFilter;
import com.f5.scholarship_backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scholarships/{scholarshipId}/documents")
public class DocumentAccessController implements DocumentAccessControllerDocs {
	private final DocumentAccessService service;

	public DocumentAccessController(DocumentAccessService service) {
		this.service = service;
	}

	@Override
	@GetMapping("/{documentId}")
	public ResponseEntity<ApiResponse<DocumentAccessResponse>> getDocumentAccess(
			@PathVariable String scholarshipId, @PathVariable String documentId, HttpServletRequest servletRequest) {
		var result = service.findAccess(scholarshipId, documentId);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.of(result, RequestIdFilter.getRequestId(servletRequest)));
	}
}
