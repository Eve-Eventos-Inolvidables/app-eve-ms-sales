package com.example.appevemssales.Service.Buy;

public class PriceGatewayException extends RuntimeException {

    private final int statusCode;

    public PriceGatewayException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public PriceGatewayException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
