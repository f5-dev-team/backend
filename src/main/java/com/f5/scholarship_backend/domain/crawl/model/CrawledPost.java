package com.f5.scholarship_backend.domain.crawl.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

public record CrawledPost(String postKey, URI sourceUrl, String title, String bodyHtml,
		LocalDate publishedDate, List<Attachment> attachments) {
	public CrawledPost {
		attachments = List.copyOf(attachments);
	}
	public record Attachment(String name, URI url) { }
}