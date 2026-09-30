package org.example.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = "org.example.matching.repository")
public class MatchingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingServiceApplication.class, args);
    }
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // This module teaches Jackson how to serialize 'Instant.now()'
        mapper.registerModule(new JavaTimeModule());
        // This makes the dates look like "2026-06-06T12:00:00Z" instead of an ugly array of numbers
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(new JdkClientHttpRequestFactory());    }
}