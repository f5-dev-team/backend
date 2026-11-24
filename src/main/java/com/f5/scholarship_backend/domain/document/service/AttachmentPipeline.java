package com.f5.scholarship_backend.domain.document.service;

import java.util.*;
import org.springframework.stereotype.Service;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;
import com.f5.scholarship_backend.domain.document.client.*;
import com.f5.scholarship_backend.domain.document.config.FileProperties;
import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;
import com.f5.scholarship_backend.domain.document.model.StoredAttachment;
import com.f5.scholarship_backend.domain.document.storage.OriginalFileStorage;

@Service
public class AttachmentPipeline {
    private final AttachmentDownloader downloader;
    private final FileValidator validator;
    private final OriginalFileStorage storage;
    private final FileProperties properties;
    public AttachmentPipeline(AttachmentDownloader downloader, FileValidator validator, OriginalFileStorage storage, FileProperties properties) {
        this.downloader = downloader; this.validator = validator; this.storage = storage; this.properties = properties;
    }
    /** 네트워크 처리는 DB 트랜잭션 밖에서 수행하고 성공한 불변 객체만 반환한다. */
    public Prepared prepare(CrawledPost post) {
        if (!post.postKey().matches("478:[0-9]+")) { throw DocumentException.download(); }
        var originals = post.attachments().stream().distinct().toList();
        long supported = originals.stream().filter(a -> OriginalFileType.fromName(a.name()).isPresent()).count();
        if (supported > properties.maxFiles()) { throw DocumentException.limitExceeded(); }
        var stored = new ArrayList<StoredAttachment>();
        long total = 0;
        boolean unsupported = false;
        for (var attachment : originals) {
            if (Thread.currentThread().isInterrupted()) { throw DocumentException.download(); }
            var type = OriginalFileType.fromName(attachment.name());
            if (type.isEmpty()) { unsupported = true; continue; }
            if (attachment.name().length() > 255 || attachment.url().toString().length() > 2048) { throw DocumentException.download(); }
            byte[] bytes = downloader.download(attachment);
            String hash = validator.validateAndHash(bytes, type.get());
            total += bytes.length;
            if (total > properties.maxPostBytes()) { throw DocumentException.limitExceeded(); }
            String key = properties.prefix().replaceAll("/+$", "") + "/GACHON_KOR/478/"
                    + post.postKey().substring(4) + "/" + hash + "." + type.get().name().toLowerCase(Locale.ROOT);
            var location = storage.store(key, bytes, hash, type.get());
            stored.add(new StoredAttachment(attachment.name(), attachment.url(), type.get(), hash, bytes.length,
                    location.bucket(), location.key(), location.versionId()));
        }
        return new Prepared(stored, unsupported);
    }
    public record Prepared(List<StoredAttachment> files, boolean unsupported) {
        public Prepared { files = List.copyOf(files); }
    }
}
