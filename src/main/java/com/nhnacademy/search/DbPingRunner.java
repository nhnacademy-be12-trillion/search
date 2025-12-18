//package com.nhnacademy.search;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class DbPingRunner implements ApplicationRunner {
//    private final JdbcTemplate jdbcTemplate;
//
//    @Override
//    public void run(ApplicationArguments args) {
//        Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
//        log.info("[DbPingRunner] DB ping result={}", one);
//
//        String version = jdbcTemplate.queryForObject("select version()", String.class);
//        log.info("[DbPingRunner] MySQL version={}", version);
//    }
//}
