package br.aof.read_opendata_apis.application.exception;

import lombok.Getter;

@Getter
public class DatabaseException extends ApplicationException {

    public DatabaseException(String message, String code) {
        super(message, code);
    }

    public DatabaseException(String message, Throwable cause, String code) {
        super(message, cause, code);
    }

}
