package com.f5.scholarship_backend.domain.document.storage;

import java.time.Instant;

public interface OriginalDocumentAccess {
	AccessUrl issue(String bucket, String key, String versionId);

	record AccessUrl(String url, Instant expiresAt) { }
}
