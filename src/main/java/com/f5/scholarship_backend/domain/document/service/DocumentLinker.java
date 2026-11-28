package com.f5.scholarship_backend.domain.document.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost.Attachment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.f5.scholarship_backend.domain.document.entity.*;
import com.f5.scholarship_backend.domain.document.model.StoredAttachment;
import com.f5.scholarship_backend.domain.document.repository.*;

@Service
public class DocumentLinker {
    private final ScholarshipDocumentRepository documents;
    private final VersionDocumentRepository links;
    private final Clock clock;
    public DocumentLinker(ScholarshipDocumentRepository documents, VersionDocumentRepository links, Clock clock) {
        this.documents = documents; this.links = links; this.clock = clock;
    }
    /** 공고 버전 저장 트랜잭션에 참여하여 파일 메타데이터와 연결을 함께 확정한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Attachment, Long> link(Long id, int version, List<StoredAttachment> files) {
        var attachmentDocuments = new HashMap<Attachment, Long>();
        var linkedDocuments = new HashSet<Long>();
        for (var file : files) {
            var document = documents.findByScholarshipIdAndSha256(id, file.sha256())
                    .orElseGet(() -> documents.saveAndFlush(new ScholarshipDocument(id, file, clock.instant())));
            if (linkedDocuments.add(document.getId())) {
                links.save(new VersionDocument(id, version, document.getId()));
            }
            attachmentDocuments.put(new Attachment(file.name(), file.sourceUrl()), document.getId());
        }
        return attachmentDocuments;
    }
}
