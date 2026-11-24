package com.f5.scholarship_backend.domain.document.storage;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.config.FileProperties;
import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;

public class S3OriginalFileStorage implements OriginalFileStorage {
    private final S3Client client;
    private final FileProperties properties;
    public S3OriginalFileStorage(S3Client client, FileProperties properties) { this.client = client; this.properties = properties; }
    @Override
    public Location store(String key, byte[] bytes, String sha256, OriginalFileType type) {
        try {
            if (Thread.currentThread().isInterrupted()) { throw DocumentException.storage(); }
            String checksum = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256));
            try {
                var response = client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                        .ifNoneMatch("*").contentType(type.contentType()).contentLength((long) bytes.length)
                        .checksumSHA256(checksum).metadata(Map.of("sha256", sha256)).build(), RequestBody.fromBytes(bytes));
                return new Location(properties.bucket(), key, response.versionId());
            } catch (S3Exception conflict) {
                if (conflict.statusCode() != 412) { throw conflict; }
                var existing = client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key)
                        .checksumMode(ChecksumMode.ENABLED).build());
                if (existing.contentLength() != bytes.length || !checksum.equals(existing.checksumSHA256())) {
                    throw DocumentException.storage();
                }
                return new Location(properties.bucket(), key, existing.versionId());
            }
        } catch (RuntimeException failure) {
            throw DocumentException.storage();
        }
    }
}
