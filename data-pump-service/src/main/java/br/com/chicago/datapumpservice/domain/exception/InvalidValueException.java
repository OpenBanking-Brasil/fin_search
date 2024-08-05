package br.com.chicago.datapumpservice.domain.exception;

public class InvalidValueException extends DomainException {

    public InvalidValueException(String message, String code) {
        super(message, code);
    }

    public InvalidValueException(String message, Throwable cause, String code) {
        super(message, cause, code);
    }

}
