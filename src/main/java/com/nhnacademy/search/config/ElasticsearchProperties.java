package com.nhnacademy.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    private String scheme;   // http
    private String host;     // s4.java21.net
    private int port;        // 9200
    private String username; // elastic
    private String password; // nhnacademy123!

    private Index index = new Index();

    @Getter
    @Setter
    public static class Index {
        private String book; // nhnacademy_books
    }
}
