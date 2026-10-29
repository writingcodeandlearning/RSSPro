package com.rsspro.net;

public class FetchResult {
    public final byte[] body;
    public final String finalUrl;
    public final String contentType;

    public FetchResult(byte[] body, String finalUrl, String contentType) {
        this.body = body;
        this.finalUrl = finalUrl;
        this.contentType = contentType;
    }
}
