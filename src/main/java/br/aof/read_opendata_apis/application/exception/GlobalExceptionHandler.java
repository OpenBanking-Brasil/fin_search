package br.aof.read_opendata_apis.application.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return buildProblem(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(InvalidTypeException.class)
    public ProblemDetail handleInvalidType(InvalidTypeException ex) {
        log.warn("Invalid type: {}", ex.getMessage());
        return buildProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(DatabaseException.class)
    public ProblemDetail handleDatabase(DatabaseException ex) {
        log.error("Database error: {}", ex.getMessage());
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro de banco de dados", ex.getCode());
    }

    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail handleApplication(ApplicationException ex) {
        log.error("Application error [{}]: {}", ex.getCode(), ex.getMessage());
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", "INTERNAL_ERROR");
    }

    private ProblemDetail buildProblem(HttpStatus status, String detail, String code) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:problem:" + code.toLowerCase().replace('_', '-')));
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
