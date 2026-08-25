package com.eazybytes.gatewayserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @GetMapping("/contactSupport")
    public Mono<String> contactSupport() {
        log.warn("Gateway fallback hit: /contactSupport, upstream circuit tripped");
        return Mono.just("An error occurred. Please try after some time or contact support team!!!");
    }

}
