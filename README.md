# search
search - api

GET /api/books/search?query={query}&page={page}&size={size}&sort={sort}

| 이름      | 타입     | 설명                                                      |
| ------- | ------ | ------------------------------------------------------- |
| `query` | String | 검색어 (제목, 저자, ISBN, 출판사 등 대상)                            |
| `page`  | int    | 0-base 페이지 번호. 기본값 0, 최소 0                              |
| `size`  | int    | 페이지 당 결과 수. 기본 10, 최대 50                                |
| `sort`  | String | 정렬 옵션. 값: `RELEVANCE`, `NEW`, `LOW_PRICE`, `HIGH_PRICE` |
