package com.nhnacademy.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    private String scheme;   
    private String host;    
    private int port;
    private String username;
    private String password;

    private Index index = new Index();

    @Getter
    @Setter
    public static class Index {
        private String book;
    }
}
