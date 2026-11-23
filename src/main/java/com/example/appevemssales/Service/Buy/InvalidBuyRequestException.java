package com.example.appevemssales.Service.Buy;

public class InvalidBuyRequestException extends RuntimeException {

    public InvalidBuyRequestException(String message) {
        super(message);
    }
}
