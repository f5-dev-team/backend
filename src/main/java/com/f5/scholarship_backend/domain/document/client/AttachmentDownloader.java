package com.f5.scholarship_backend.domain.document.client;

import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;

public interface AttachmentDownloader {
    byte[] download(CrawledPost.Attachment attachment);
}
