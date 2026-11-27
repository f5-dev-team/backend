package com.f5.scholarship_backend.domain.document.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.storage.*;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class DocumentConfig {
    /** 인증 키를 코드에 넣지 않고 SDK 기본 인증 체인으로 로컬·서버 권한을 찾는다. */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "document-storage.enabled", havingValue = "true")
    S3Client originalS3Client(FileProperties p) {
        return S3Client.builder().region(Region.of(p.region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(60)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(90))
                        .apiCallAttemptTimeout(Duration.ofSeconds(60))).build();
    }
    @Bean
    @ConditionalOnProperty(name = "document-storage.enabled", havingValue = "true")
    OriginalFileStorage s3OriginalFileStorage(S3Client client, FileProperties p) {
        return new S3OriginalFileStorage(client, p);
    }
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "document-storage.enabled", havingValue = "true")
    S3Presigner originalS3Presigner(FileProperties properties) {
        return S3Presigner.builder().region(Region.of(properties.region())).build();
    }

    @Bean
    @ConditionalOnProperty(name = "document-storage.enabled", havingValue = "true")
    OriginalDocumentAccess s3OriginalDocumentAccess(S3Client client, S3Presigner presigner,
            @Value("${document-storage.access-url-ttl-seconds}") long ttlSeconds) {
        return new S3OriginalDocumentAccess(client, presigner, Duration.ofSeconds(ttlSeconds));
    }

    @Bean
    @ConditionalOnProperty(name = "document-storage.enabled", havingValue = "false", matchIfMissing = true)
    OriginalDocumentAccess disabledOriginalDocumentAccess() {
        return (bucket, key, versionId) -> { throw DocumentException.storage(); };
    }

    /** 비활성 환경에서 가짜 성공을 반환하지 않으며 실제 파일 저장 요청은 명시적으로 실패시킨다. */
    @Bean
    @ConditionalOnProperty(name = "document-storage.enabled", havingValue = "false", matchIfMissing = true)
    OriginalFileStorage disabledOriginalFileStorage() { return (key, bytes, hash, type) -> { throw DocumentException.storage(); }; }
}
