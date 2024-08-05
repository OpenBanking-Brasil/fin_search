package br.com.chicago.datapumpservice.domain.exception;

import com.google.common.base.CaseFormat;
import lombok.Getter;

import javax.validation.ConstraintViolation;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Getter
public class ValidationException extends RuntimeException {

    private static final Map<String, String> CODES = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();

    public ValidationException(Set<? extends ConstraintViolation<?>> violations) {
        this.initCodes();

        violations.forEach(violation -> {
            String message = violation.getRootBeanClass().getSimpleName().toLowerCase(Locale.ROOT) + ".";
            message += CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, violation.getPropertyPath().toString());
            message += " " + violation.getMessage();

            this.errors.put(
                    message,
                    CODES.get(violation.getConstraintDescriptor().getAnnotation().annotationType().getName())
            );
        });
    }

    private void initCodes() {
        CODES.put("javax.validation.constraints.NotNull", "1000");
        CODES.put("javax.validation.constraints.NotBlank", "1000");
        CODES.put("javax.validation.constraints.Min", "1001");
        CODES.put("javax.validation.constraints.Max", "1002");
        CODES.put("javax.validation.constraints.Size", "1003");
        CODES.put("javax.validation.constraints.Pattern", "1005");
        CODES.put("javax.validation.constraints.Email", "1010");
        CODES.put("org.hibernate.validator.constraints.br.CPF", "1011");
        CODES.put("org.hibernate.validator.constraints.br.CNPJ", "1012");
        CODES.put("javax.validation.constraints.NotEmpty", "1013");
    }

}
