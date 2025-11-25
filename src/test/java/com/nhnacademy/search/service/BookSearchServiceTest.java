package com.nhnacademy.search.service;

import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.dto.BookSearchRequest;
import com.nhnacademy.search.dto.BookSearchResponse;
import com.nhnacademy.search.dto.BookSearchResult;
import com.nhnacademy.search.dto.BookSortOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookSearchServiceTest {

    // WebClient 체이닝(post().uri().bodyValue().retrieve()...) 때문에 DEEP_STUB 사용
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    WebClient elasticsearchWebClient;

    // properties.getIndex().getBook() 체이닝도 DEEP_STUB 사용
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ElasticsearchProperties properties;

    @InjectMocks
    BookSearchService bookSearchService;

    @Test
    @DisplayName("검색 성공 - ES 응답을 DTO로 매핑")
    void search_success() {
        // given
        when(properties.getIndex().getBook()).thenReturn("nhnacademy_books");

        // ES 응답 mock 데이터 구성
        Map<String, Object> esResponse = new HashMap<>();

        Map<String, Object> total = new HashMap<>();
        total.put("value", 1);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("isbn", "9788954640718");
        metadata.put("title", "흰 :한강 소설");
        metadata.put("author", "지은이: 한강");
        metadata.put("publisher", "문학동네");
        metadata.put("price", 11500);
        metadata.put("imageUrl", "http://image.aladin.co.kr/product/8466/1/cover/k712535369_1.jpg");
        metadata.put("editionPublishDate", "2016-05-25");

        Map<String, Object> source = new HashMap<>();
        source.put("id", "3153205");
        source.put("metadata", metadata);

        Map<String, Object> hit = new HashMap<>();
        hit.put("_source", source);
        hit.put("_score", 1183.2351f);

        Map<String, Object> hits = new HashMap<>();
        hits.put("total", total);
        hits.put("hits", List.of(hit));

        esResponse.put("hits", hits);

        // WebClient 체인 한 번에 스텁
        when(
                elasticsearchWebClient
                        .post()
                        .uri(anyString())
                        .bodyValue(any())
                        .retrieve()
                        .bodyToMono(any(ParameterizedTypeReference.class))
        ).thenReturn(Mono.just(esResponse));

        // BookSearchRequest(String query, BookSortOption sort, int page, int size)
        BookSearchRequest request =
                new BookSearchRequest("한강", BookSortOption.RELEVANCE, 0, 10);

        // when
        BookSearchResponse response = bookSearchService.search(request);

        // then
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.results()).hasSize(1);

        BookSearchResult result = response.results().get(0);
        assertThat(result.id()).isEqualTo("3153205");
        assertThat(result.isbn()).isEqualTo("9788954640718");
        assertThat(result.title()).isEqualTo("흰 :한강 소설");
        assertThat(result.author()).isEqualTo("지은이: 한강");
        assertThat(result.publisher()).isEqualTo("문학동네");
        assertThat(result.price()).isEqualTo(11500);
        assertThat(result.imageUrl()).isEqualTo("http://image.aladin.co.kr/product/8466/1/cover/k712535369_1.jpg");
        assertThat(result.editionPublishDate()).isEqualTo("2016-05-25");
        assertThat(result.score()).isEqualTo(1183.2351f);
    }

    @Test
    @DisplayName("가격 없는 도서 - price는 null로 매핑")
    void search_priceNull() {
        // given
        when(properties.getIndex().getBook()).thenReturn("nhnacademy_books");

        Map<String, Object> esResponse = new HashMap<>();

        Map<String, Object> total = new HashMap<>();
        total.put("value", 1);

        // price, imageUrl, editionPublishDate 일부러 생략/없음
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("isbn", "9788986766080");
        metadata.put("title", "한강 수계 관리에 따른 수도권지역의 경비 분담 연구");
        metadata.put("author", "연구책임: 최승업 ;연구자문 및 일부집필: 최연홍");
        metadata.put("publisher", "강원개발연구원");
        // metadata.put("price", null);  // 아예 없는 케이스

        Map<String, Object> source = new HashMap<>();
        source.put("id", "1026048");
        source.put("metadata", metadata);

        Map<String, Object> hit = new HashMap<>();
        hit.put("_source", source);
        hit.put("_score", 819.95154f);

        Map<String, Object> hits = new HashMap<>();
        hits.put("total", total);
        hits.put("hits", List.of(hit));

        esResponse.put("hits", hits);

        when(
                elasticsearchWebClient
                        .post()
                        .uri(anyString())
                        .bodyValue(any())
                        .retrieve()
                        .bodyToMono(any(ParameterizedTypeReference.class))
        ).thenReturn(Mono.just(esResponse));

        BookSearchRequest request =
                new BookSearchRequest("한강", BookSortOption.RELEVANCE, 0, 10);

        // when
        BookSearchResponse response = bookSearchService.search(request);

        // then
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.results()).hasSize(1);

        BookSearchResult result = response.results().get(0);
        assertThat(result.id()).isEqualTo("1026048");
        assertThat(result.title()).isEqualTo("한강 수계 관리에 따른 수도권지역의 경비 분담 연구");
        assertThat(result.price()).isNull();            // ⭐ 핵심: price는 null
        assertThat(result.score()).isEqualTo(819.95154f);
    }

    @Test
    @DisplayName("LOW_PRICE 정렬 - ES 쿼리 sort: price asc + missing _last + score desc")
    void search_lowPriceSort_buildsProperSort() {
        // given
        when(properties.getIndex().getBook()).thenReturn("nhnacademy_books");

        // 결과는 안 중요하니 최소 형태로만 구성
        Map<String, Object> esResponse = new HashMap<>();
        Map<String, Object> hits = new HashMap<>();
        hits.put("total", Map.of("value", 0));
        hits.put("hits", List.of());
        esResponse.put("hits", hits);

        // bodyValue()로 넘어가는 ES 쿼리 Map을 캡쳐
        ArgumentCaptor<Map<String, Object>> bodyCaptor =
                ArgumentCaptor.forClass(Map.class);

        when(
                elasticsearchWebClient
                        .post()
                        .uri(anyString())
                        .bodyValue(bodyCaptor.capture())
                        .retrieve()
                        .bodyToMono(any(ParameterizedTypeReference.class))
        ).thenReturn(Mono.just(esResponse));

        BookSearchRequest req =
                new BookSearchRequest("한강", BookSortOption.LOW_PRICE, 0, 10);

        // when
        bookSearchService.search(req);

        // then - bodyValue()로 전달된 ES 쿼리 확인
        Map<String, Object> esQuery = bodyCaptor.getValue();
        assertThat(esQuery).isNotNull();
        assertThat(esQuery.get("sort")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sort =
                (List<Map<String, Object>>) esQuery.get("sort");

        assertThat(sort).hasSize(2);

        // 1순위: 가격 정렬 (metadata.price asc, missing _last)
        Map<String, Object> priceSort = sort.get(0);
        assertThat(priceSort).hasSize(1);

        Map.Entry<String, Object> priceEntry =
                priceSort.entrySet().iterator().next();
        assertThat(priceEntry.getKey()).isEqualTo("metadata.price");

        @SuppressWarnings("unchecked")
        Map<String, Object> priceOptions =
                (Map<String, Object>) priceEntry.getValue();
        assertThat(priceOptions.get("order")).isEqualTo("asc");
        assertThat(priceOptions.get("missing")).isEqualTo("_last"); // ⭐ 가격 없는 문서는 뒤로

        // 2순위: 같은 가격 안에서는 score desc
        Map<String, Object> scoreSort = sort.get(1);
        assertThat(scoreSort).containsKey("_score");

        @SuppressWarnings("unchecked")
        Map<String, Object> scoreOptions =
                (Map<String, Object>) scoreSort.get("_score");
        assertThat(scoreOptions.get("order")).isEqualTo("desc");
    }
}
