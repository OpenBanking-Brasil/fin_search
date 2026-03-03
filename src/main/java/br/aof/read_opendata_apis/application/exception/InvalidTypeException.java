package br.aof.read_opendata_apis.application.exception;

import lombok.Getter;

@Getter
public class InvalidTypeException extends ApplicationException {

    public InvalidTypeException(String message, String code) {
        super(message, code);
    }

    public InvalidTypeException(String message, Throwable cause, String code) {
        super(message, cause, code);
    }

}
