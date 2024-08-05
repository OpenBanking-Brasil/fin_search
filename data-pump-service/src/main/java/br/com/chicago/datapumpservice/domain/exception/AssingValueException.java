package br.com.chicago.datapumpservice.domain.exception;

public class AssingValueException extends DomainException {

    public AssingValueException(String message, String code) {
        super(message, code);
    }

    public AssingValueException(String message, Throwable cause, String code) {
        super(message, cause, code);
    }

}
