package com.f5.scholarship_backend.domain.document.storage;

import java.time.Duration;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public class S3OriginalDocumentAccess implements OriginalDocumentAccess {
	private final S3Client client;
	private final S3Presigner presigner;
	private final Duration ttl;

	public S3OriginalDocumentAccess(S3Client client, S3Presigner presigner, Duration ttl) {
		if (ttl.compareTo(Duration.ofSeconds(1)) < 0 || ttl.compareTo(Duration.ofDays(7)) > 0) {
			throw new IllegalArgumentException("S3 원본 접근 URL 유효기간은 1초에서 7일 사이여야 합니다.");
		}
		this.client = client;
		this.presigner = presigner;
		this.ttl = ttl;
	}

	@Override
	public AccessUrl issue(String bucket, String key, String versionId) {
		try {
			// Signing alone does not verify that this exact object version can be read.
			client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).versionId(versionId)
					.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(3))
							.apiCallAttemptTimeout(Duration.ofSeconds(2)))
					.build());

			var request = GetObjectRequest.builder().bucket(bucket).key(key).versionId(versionId).build();
			var signed = presigner.presignGetObject(builder -> builder.signatureDuration(ttl).getObjectRequest(request));
			return new AccessUrl(signed.url().toExternalForm(), signed.expiration());
		} catch (SdkException | IllegalArgumentException failure) {
			throw DocumentException.storage();
		}
	}
}
