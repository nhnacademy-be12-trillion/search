//package com.nhnacademy.search;
//
//import com.nhnacademy.search.client.GeminiClient;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class GeminiSmokeRunner implements ApplicationRunner {
//    private final GeminiClient geminiClient;
//
//    @Override
//    public void run(ApplicationArguments args) {
//        String out = geminiClient.generateText("한 문장으로 '클린 코드'가 뭔지 설명해줘.");
//        log.info("[GeminiSmokeRunner] {}", out);
//    }
//}
//
