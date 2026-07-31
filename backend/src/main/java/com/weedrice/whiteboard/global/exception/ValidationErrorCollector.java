package com.weedrice.whiteboard.global.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ValidationErrorCollector {

    private static final String DEFAULT_FIELD = "request";

    private final MessageSource messageSource;

    public Map<String, List<String>> collect(MethodArgumentNotValidException exception) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.computeIfAbsent(error.getField(), key -> new ArrayList<>())
                        .add(error.getDefaultMessage()));
        if (errors.isEmpty()) {
            exception.getBindingResult().getGlobalErrors().forEach(error ->
                    errors.computeIfAbsent(error.getObjectName(), key -> new ArrayList<>())
                            .add(error.getDefaultMessage()));
        }
        return errors;
    }

    public Map<String, List<String>> collect(ConstraintViolationException exception) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Set<ConstraintViolation<?>> violations = exception.getConstraintViolations();
        if (violations != null) {
            for (ConstraintViolation<?> violation : violations) {
                errors.computeIfAbsent(violationFieldName(violation), key -> new ArrayList<>())
                        .add(violation.getMessage());
            }
        }
        return errors;
    }

    public Map<String, List<String>> collect(HandlerMethodValidationException exception) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        exception.visitResults(new HandlerMethodValidationException.Visitor() {
            @Override
            public void requestParam(RequestParam annotation, ParameterValidationResult result) {
                collectResult(annotatedName(annotation.name(), result), result);
            }

            @Override
            public void requestHeader(RequestHeader annotation, ParameterValidationResult result) {
                collectResult(annotatedName(annotation.name(), result), result);
            }

            @Override
            public void pathVariable(PathVariable annotation, ParameterValidationResult result) {
                collectResult(annotatedName(annotation.name(), result), result);
            }

            @Override
            public void cookieValue(CookieValue annotation, ParameterValidationResult result) {
                collectResult(annotatedName(annotation.name(), result), result);
            }

            @Override
            public void matrixVariable(MatrixVariable annotation, ParameterValidationResult result) {
                collectResult(annotatedName(annotation.name(), result), result);
            }

            @Override
            public void modelAttribute(ModelAttribute annotation, ParameterErrors parameterErrors) {
                collectFieldErrors(parameterErrors);
            }

            @Override
            public void requestBody(RequestBody annotation, ParameterErrors parameterErrors) {
                collectFieldErrors(parameterErrors);
            }

            @Override
            public void requestBodyValidationResult(
                    RequestBody annotation,
                    ParameterValidationResult result) {
                collectResult(parameterName(result), result);
            }

            @Override
            public void requestPart(RequestPart annotation, ParameterErrors parameterErrors) {
                collectFieldErrors(parameterErrors);
            }

            @Override
            public void other(ParameterValidationResult result) {
                collectResult(parameterName(result), result);
            }

            private String annotatedName(String annotationName, ParameterValidationResult result) {
                return annotationName != null && !annotationName.isBlank()
                        ? annotationName
                        : parameterName(result);
            }

            private void collectFieldErrors(ParameterErrors parameterErrors) {
                if (parameterErrors.getFieldErrorCount() == 0) {
                    collectResult(parameterName(parameterErrors), parameterErrors);
                    return;
                }
                parameterErrors.getFieldErrors().forEach(fieldError ->
                        errors.computeIfAbsent(fieldError.getField(), key -> new ArrayList<>())
                                .add(resolveMessage(fieldError)));
            }

            private void collectResult(String field, ParameterValidationResult result) {
                List<String> messages = errors.computeIfAbsent(field, key -> new ArrayList<>());
                result.getResolvableErrors().forEach(error -> messages.add(resolveMessage(error)));
            }
        });
        return errors;
    }

    public Map<String, List<String>> singleFieldError(String field, String messageKey) {
        if (field == null || field.isBlank()) {
            return Map.of();
        }
        String message = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(field, new ArrayList<>(List.of(message)));
        return errors;
    }

    private String violationFieldName(ConstraintViolation<?> violation) {
        Path propertyPath = violation.getPropertyPath();
        if (propertyPath == null) {
            return DEFAULT_FIELD;
        }

        List<String> properties = new ArrayList<>();
        String lastParameter = null;
        for (Path.Node node : propertyPath) {
            if (node.getName() == null || node.getName().isBlank()) {
                continue;
            }
            if (node.getKind() == ElementKind.PROPERTY) {
                properties.add(node.getName());
            } else if (node.getKind() == ElementKind.PARAMETER) {
                lastParameter = node.getName();
            }
        }
        if (!properties.isEmpty()) {
            return String.join(".", properties);
        }
        return lastParameter != null ? lastParameter : DEFAULT_FIELD;
    }

    private String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name != null ? name : DEFAULT_FIELD;
    }

    private String resolveMessage(MessageSourceResolvable error) {
        try {
            return messageSource.getMessage(error, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException exception) {
            return error.getDefaultMessage() != null ? error.getDefaultMessage() : "";
        }
    }
}
