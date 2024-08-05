package br.com.chicago.datapumpservice.application.exception;

import lombok.Getter;

@Getter
public abstract class ApplicationException extends RuntimeException {

    protected final String code;

    protected ApplicationException(String message, String code) {
        super(message);

        this.code = code;
    }

    protected ApplicationException(String message, Throwable cause, String code) {
        super(message, cause);

        this.code = code;
    }

}
