package br.com.chicago.datapumpservice.application.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends ApplicationException {

    public NotFoundException(String message, String code) {
        super(message, code);
    }

    public NotFoundException(String message, Throwable cause, String code) {
        super(message, cause, code);
    }

}
