package com.f5.scholarship_backend.domain.document;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.storage.S3OriginalDocumentAccess;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3OriginalDocumentAccessTest {
	@Test
	void checksAndSignsTheSameObjectVersionUsingTheConfiguredLifetime() {
		S3Client s3 = mock(S3Client.class);
		try (var presigner = S3Presigner.builder().region(Region.AP_NORTHEAST_2)
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("fixture-key", "fixture-secret")))
				.build()) {
			var access = new S3OriginalDocumentAccess(s3, presigner, Duration.ofMinutes(5));
			Instant before = Instant.now();
			var result = access.issue("fixture-bucket", "scholarship-originals/file.pdf", "original-v1");

			var head = ArgumentCaptor.forClass(HeadObjectRequest.class);
			verify(s3).headObject(head.capture());
			assertThat(head.getValue().bucket()).isEqualTo("fixture-bucket");
			assertThat(head.getValue().key()).isEqualTo("scholarship-originals/file.pdf");
			assertThat(head.getValue().versionId()).isEqualTo("original-v1");

			var url = URI.create(result.url());
			assertThat(url.getScheme()).isEqualTo("https");
			assertThat(url.getHost()).startsWith("fixture-bucket.");
			assertThat(url.getPath()).isEqualTo("/scholarship-originals/file.pdf");
			assertThat(url.getRawQuery()).contains("versionId=original-v1", "X-Amz-Expires=300", "X-Amz-Signature=");
			assertThat(result.expiresAt()).isBetween(before.plusSeconds(299), Instant.now().plusSeconds(301));
		}
	}

	@Test
	void refusesToSignMissingObjectsAndHidesStorageErrors() {
		S3Client s3 = mock(S3Client.class);
		S3Presigner presigner = mock(S3Presigner.class);
		var access = new S3OriginalDocumentAccess(s3, presigner, Duration.ofMinutes(5));
		when(s3.headObject(any(HeadObjectRequest.class)))
				.thenThrow(S3Exception.builder().statusCode(404).message("internal S3 object details").build());

		assertThatThrownBy(() -> access.issue("fixture-bucket", "scholarship-originals/missing.pdf", null))
				.isInstanceOf(DocumentException.class)
				.hasMessageNotContaining("internal S3 object details");
		org.mockito.Mockito.verifyNoInteractions(presigner);
	}
}
