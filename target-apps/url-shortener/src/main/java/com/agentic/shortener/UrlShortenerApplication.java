package com.agentic.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** Entry point of the URL shortener service. */
@SpringBootApplication
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }

    /** One clock for the whole application so time-dependent rules are easy to test. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
