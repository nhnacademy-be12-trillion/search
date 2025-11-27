package com.nhnacademy.search;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DbPingRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
        System.out.println("DB ping result = " + one);

        String version = jdbcTemplate.queryForObject("select version()", String.class);
        System.out.println("MySQL version = " + version);
    }
}
