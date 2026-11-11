package com.f5.scholarship_backend.domain.crawl.client;

/** 네트워크·게시판 구조 오류를 내부 메시지나 원문 없이 전달한다. */
public class CrawlException extends RuntimeException {
	public CrawlException() { super("공고 수집을 완료하지 못했습니다."); }
}