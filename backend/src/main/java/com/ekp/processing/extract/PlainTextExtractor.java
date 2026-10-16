package com.ekp.processing.extract;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Plain text: decode the bytes as UTF-8. */
@Component
public class PlainTextExtractor implements TextExtractor {

    @Override
    public String contentType() {
        return "text/plain";
    }

    @Override
    public String extract(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
