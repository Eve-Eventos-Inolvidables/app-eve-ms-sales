package com.example.appevemssales.Service.Buy;

public class StockUnavailableException extends RuntimeException {

    public StockUnavailableException(String message) {
        super(message);
    }
}
