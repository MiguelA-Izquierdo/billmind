package dev.izquierdo.billmind.market.domain.exceptions;

public class InvalidElectricityRateException extends RuntimeException {

    public InvalidElectricityRateException(String message) {
        super(message);
    }
}