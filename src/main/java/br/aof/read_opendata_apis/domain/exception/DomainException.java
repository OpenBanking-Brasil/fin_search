package br.aof.read_opendata_apis.domain.exception;

import lombok.Getter;

@Getter
public abstract class DomainException extends RuntimeException {

    protected final String code;

    protected DomainException(String message, String code) {
        super(message);

        this.code = code;
    }

    protected DomainException(String message, Throwable cause, String code) {
        super(message, cause);

        this.code = code;
    }

}
