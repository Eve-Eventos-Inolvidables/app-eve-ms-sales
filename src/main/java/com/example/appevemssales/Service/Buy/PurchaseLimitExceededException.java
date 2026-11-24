package com.example.appevemssales.Service.Buy;

public class PurchaseLimitExceededException extends RuntimeException {

    public PurchaseLimitExceededException(String message) {
        super(message);
    }
}
