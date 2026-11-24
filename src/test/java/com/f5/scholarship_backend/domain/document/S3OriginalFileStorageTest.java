package com.f5.scholarship_backend.domain.document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;
import com.f5.scholarship_backend.domain.document.storage.S3OriginalFileStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class S3OriginalFileStorageTest {
    private final S3Client client = mock(S3Client.class);
    private final byte[] bytes = "%PDF-1.7 합성".getBytes(StandardCharsets.UTF_8);
    private String hash() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    @Test void uploadsWithPreconditionAndChecksumWithoutPublicAcl() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().versionId("v1").build());
        var result = new S3OriginalFileStorage(client, FileValidatorTest.properties()).store("path/a.pdf", bytes, hash(), OriginalFileType.PDF);
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");
        assertThat(request.getValue().checksumSHA256()).isEqualTo(Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(request.getValue().acl()).isNull();
        assertThat(result.versionId()).isEqualTo("v1");
    }
    @Test void reusesOnlyVerifiedExistingObject() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(S3Exception.builder().statusCode(412).build());
        String checksum = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength((long) bytes.length).checksumSHA256(checksum).versionId("old").build());
        var storage = new S3OriginalFileStorage(client, FileValidatorTest.properties());
        assertThat(storage.store("path/a.pdf", bytes, hash(), OriginalFileType.PDF).versionId()).isEqualTo("old");
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength((long) bytes.length).checksumSHA256("wrong").build());
        assertThatThrownBy(() -> storage.store("path/a.pdf", bytes, hash(), OriginalFileType.PDF)).isInstanceOf(DocumentException.class);
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
    @Test void accessFailureDoesNotAttemptOverwriteOrLeakProviderMessage() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("민감한 합성 메시지").build());
        assertThatThrownBy(() -> new S3OriginalFileStorage(client, FileValidatorTest.properties())
                .store("path/a.pdf", bytes, hash(), OriginalFileType.PDF))
                .isInstanceOf(DocumentException.class).hasMessageNotContaining("민감한");
        verify(client, never()).headObject(any(HeadObjectRequest.class));
    }
}
