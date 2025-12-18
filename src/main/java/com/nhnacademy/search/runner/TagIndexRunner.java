//package com.nhnacademy.search.runner;
//
//import com.nhnacademy.search.config.ElasticsearchProperties;
//import com.nhnacademy.search.service.TagIndexService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class TagIndexRunner implements ApplicationRunner {
//
//    private final TagIndexService tagIndexService;
//    private final ElasticsearchProperties esProps;
//
//    @Override
//    public void run(ApplicationArguments args) {
//        String indexName = esProps.getIndex().getBook();
//
//        int pageSize = 500;
//
//        tagIndexService.syncTagsToEs(indexName, pageSize);
//        log.info("[TagIndexRunner] tag sync finished. index={}", indexName);
//    }
//}
