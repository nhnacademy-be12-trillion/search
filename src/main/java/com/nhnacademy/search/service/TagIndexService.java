package com.nhnacademy.search.service;

import com.nhnacademy.search.es.EsTagUpdater;
import com.nhnacademy.search.repository.BookTagReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TagIndexService {

    private final BookTagReadRepository repo;
    private final EsTagUpdater esTagUpdater;

    public void syncTagsToEs(String indexName, int pageSize) {
        long lastBookId = 0;

        while (true) {
            var rows = repo.findBookTagsAfter(lastBookId, pageSize);
            if (rows.isEmpty()) break;

            Map<String, List<String>> isbnToTags = new HashMap<>();

            for (var r : rows) {
                lastBookId = Math.max(lastBookId, r.bookId());

                List<String> tags = (r.tagsCsv() == null || r.tagsCsv().isBlank())
                        ? List.of()
                        : Arrays.stream(r.tagsCsv().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

                if (r.isbn() != null && !r.isbn().isBlank()) {
                    isbnToTags.put(r.isbn(), tags);
                }
            }

            // 업데이트할 ISBN이 없으면 ES 호출 스킵
            if (isbnToTags.isEmpty()) {
                continue;
            }

            esTagUpdater.updateMetadataTagsByIsbn(indexName, isbnToTags);
        }
    }
}
