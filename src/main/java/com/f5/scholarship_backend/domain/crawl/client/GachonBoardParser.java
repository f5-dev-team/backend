package com.f5.scholarship_backend.domain.crawl.client;

import java.net.URI;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import com.f5.scholarship_backend.domain.crawl.model.BoardPage;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class GachonBoardParser {
	private static final Pattern POST = Pattern.compile("/bbs/kor/478/([0-9]+)/artclView\\.do");
	private static final Pattern PAGE = Pattern.compile("page_link\\(['\"]([0-9]+)['\"]\\)");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu.MM.dd");

	/** 목록의 글번호·게시일·고정 여부와 다음 페이지 존재 여부를 추출한다. */
	public BoardPage parseList(String html, URI base, int page) {
		var document = Jsoup.parse(html, base.toString());
		List<BoardPage.Entry> entries = new ArrayList<>();
		for (Element row : document.select("tr:has(td.td-subject)")) {
			Element anchor = row.selectFirst("td.td-subject a[href]");
			Element date = row.selectFirst("td.td-date");
			if (anchor == null || date == null) { throw new CrawlException(); }
			URI url = base.resolve(anchor.attr("href"));
			var matcher = POST.matcher(url.getPath());
			if (!matcher.matches()) { throw new CrawlException(); }
			entries.add(new BoardPage.Entry("478:" + matcher.group(1), url,
					LocalDate.parse(date.text().strip(), DATE), row.hasClass("notice")));
		}
		// 인증 페이지·오류 페이지·변경된 HTML을 정상적인 빈 수집으로 취급하지 않는다.
		if (entries.isEmpty()) { throw new CrawlException(); }
		boolean hasNext = false;
		for (Element anchor : document.select("a[href]")) {
			var matcher = PAGE.matcher(anchor.attr("href"));
			if (matcher.find() && Integer.parseInt(matcher.group(1)) > page) { hasNext = true; }
		}
		return new BoardPage(entries, hasNext);
	}

	/** 상세의 본문 구조와 글번호를 검증하고 첨부 링크를 원문과 함께 보존한다. */
	public CrawledPost parseDetail(String html, BoardPage.Entry entry) {
		var document = Jsoup.parse(html, entry.url().toString());
		Element number = document.selectFirst(".view-num dd");
		Element title = document.selectFirst(".view-title");
		Element body = document.selectFirst(".view-con");
		if (number == null || title == null || body == null || title.text().isBlank()
				|| !entry.postKey().equals("478:" + number.text().strip())) {
			throw new CrawlException();
		}
		body.select("script, style").remove();
		document.outputSettings().prettyPrint(false);
		List<CrawledPost.Attachment> attachments = new ArrayList<>();
		for (Element anchor : document.select(".view-file a[href]")) {
			URI uri = entry.url().resolve(anchor.attr("href"));
			if (uri.getPath() != null && uri.getPath().matches("/bbs/kor/478/[0-9]+/download\\.do")) {
				attachments.add(new CrawledPost.Attachment(normalize(anchor.text()), uri));
			}
		}
		return new CrawledPost(entry.postKey(), entry.url(), normalize(title.text()),
				normalize(body.html()), entry.date(), attachments);
	}

	/** 정규화는 Unicode NFC·줄바꿈·앞뒤 공백에만 적용한다. */
	private String normalize(String value) {
		return Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC).strip();
	}
}