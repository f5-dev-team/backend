package com.f5.scholarship_backend.domain.crawl.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

public record BoardPage(List<Entry> entries, boolean hasNext) {
	public BoardPage { entries = List.copyOf(entries); }
	public record Entry(String postKey, URI url, LocalDate date, boolean pinned) { }
}