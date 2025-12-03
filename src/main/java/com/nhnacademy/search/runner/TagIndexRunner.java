package com.nhnacademy.search.runner;

import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.service.TagIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TagIndexRunner implements ApplicationRunner {

    private final TagIndexService tagIndexService;
    private final ElasticsearchProperties esProps;

    @Override
    public void run(ApplicationArguments args) {
        String indexName = esProps.getIndex().getBook();

        int pageSize = 500;

        tagIndexService.syncTagsToEs(indexName, pageSize);
        System.out.println("[TagIndexRunner] tag sync finished. index=" + indexName);
    }
}
