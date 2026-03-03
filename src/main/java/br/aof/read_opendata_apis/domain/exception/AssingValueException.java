package br.aof.read_opendata_apis.domain.exception;

public class AssingValueException extends DomainException {

    public AssingValueException(String message, String code) {
        super(message, code);
    }

    public AssingValueException(String message, Throwable cause, String code) {
        super(message, cause, code);
    }

}
