# Search Service (Elasticsearch)
**일반 검색([BM25](https://wikidocs.net/289869))** 과 **AI 검색([Embedding Vector](https://www.elastic.co/what-is/vector-embedding) + Reranker + LLM)** 을 제공하는 검색 서버입니다.

---

## 프로젝트 소개
Search Service는 도서 데이터를 Elasticsearch에 인덱싱하여 **빠르고 정확한 키워드 검색(BM25)** 과  
**자연어 질의에 강한 AI 검색(Embedding Vector)** 을 제공하는 서비스입니다.

키워드가 정확히 일치하지 않아도 의미가 비슷한 도서를 찾아주며, Reranker와 LLM 으로 상위 결과 품질을 추가로 개선할 수 있습니다.

---

## 무엇을 해결하는지
기존 키워드 검색만으로는 다음 문제가 자주 발생합니다.

- 같은 의미지만 표현이 다르면 검색이 잘 안 됨 (예: “입문”, “초보”, “기초”)
- 자연어 질의(“~ 추천해줘”, “쉽게 설명한 책”)에 취약
- 결과는 나오지만 **상위 정렬 품질**이 아쉬움

Search Service는 아래 조합으로 이를 개선합니다.

- **BM25**: 정확한 키워드 매칭/필드 가중치로 기본 검색 품질 확보  
- **Vector Search**: 의미 기반 유사도 검색으로 검색 누락 감소  
- **Reranker**: 후보 결과를 재정렬해 상위 결과의 “추천성/적합도” 향상
- **LLM**: 재정렬된 상위 결과를 Gemini API 를 이용하여 다시 검증/정렬 하고, 상위 결과에 대한 추천 이유 작성

---

## 주요 기능

### 1) 일반 검색 (BM25)
- `Korean (nori) analysis plugin` analyzer 기반 텍스트 검색
- 필드 가중치 적용(제목 > 저자 > 태그 > ISBN > 출판사 > 도서 설명 > 리뷰 내용)으로 정확도 개선
- 정렬 옵션 지원
  - 신상품(발행일), 가격(최저/최고), 인기도, 평점, 리뷰수 등

### 2) AI 검색 (Embedding Vector)
- 질의를 **1024차원 임베딩 벡터**로 변환 (Ollama API)
- ES `embeddingVector`에서 cosine 유사도 기반 kNN 검색 수행
- 키워드가 달라도 의미가 비슷한 도서를 상위에 노출

### 3) Rerank
- Vector 검색 TopK 후보를 LLM/리랭커로 재정렬하여 품질 개선
- 비용/지연을 고려해 TopN 제한 및 타임아웃/캐시 적용
- 장애 시 fallback(BM25 기본 검색) 전략 구성 

### 4) Elasticsearch 인덱스 요약
- Index: `trillion_books_dev_v2`
- Vector Field: `embeddingVector`
  - `dims: 1024`, `similarity: cosine`, `index_options: int8_hnsw`
- 주요 검색 데이터(요약)
  - `metadata.title/author/publisher/content/reviewSummary/tags`
  - `metadata.editionPublishDate`, `metadata.price/salePrice`
  - `popularityScore`, `ratingAvg`, `reviewCount` 등
- 참고
  - `metadata.imageUrl`은 `index:false`로 검색/필터/정렬에는 사용하지 않음(표시용)

---

## 시작 가이드

### 1) 필수 준비
- java 21 LTS
- Elastic Search : 8.x
- (AI 검색 사용 시) Embedding Provider 준비 (예: Ollama, Reranker 서비스, Gemini API Key)

### 2) 환경변수
| 환경변수                | 사용 위치                                        | 설명                                   |
| ------------------- | -------------------------------------------- | ------------------------------------ |
| `ES_HOST`           | `elasticsearch.host: ${ES_HOST}`             | Elasticsearch 호스트                    |
| `ES_PASSWORD`       | `elasticsearch.password: ${ES_PASSWORD}`     | Elasticsearch 비밀번호                   |
| `DB_PASSWORD`       | `spring.datasource.password: ${DB_PASSWORD}` | MySQL 비밀번호                           |
| `REDIS_PW`          | `spring.data.redis.password: ${REDIS_PW}`    | Redis 비밀번호                           |
| `GEMINI_SEARCH_KEY` | `gemini.api-key: ${GEMINI_SEARCH_KEY}`       | Gemini API Key                       |
| `OLLAMA_BASE_URL`   | `ai.ollama.base-url: ${OLLAMA_BASE_URL}`     | Ollama Embedding 서버 Base URL         |
| `RERANKER_BASE_URL` | `ai.reranker.base-url: ${RERANKER_BASE_URL}` | Reranker 서버 Base URL                 |

