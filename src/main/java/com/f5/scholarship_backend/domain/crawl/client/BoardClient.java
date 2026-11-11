package com.f5.scholarship_backend.domain.crawl.client;

import java.net.URI;

public interface BoardClient {
	/** 설정된 게시판과 같은 원천의 목록·상세 HTML만 조회한다. */
	String get(URI uri);
}