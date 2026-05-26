package me.serenityline.api.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import me.serenityline.api.auth.exception.TooManyLoginAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Pattern MESSAGE_KEY_PATTERN = Pattern.compile(
            "[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)+"
    );

    private static final int MAX_LOG_MESSAGE_LENGTH = 1_000;

    private static final String ROOT_FIELD = "$";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError error = resolveSafeError(
                exception.getMessage(),
                "error.badRequest",
                locale
        );

        logClientError(
                request,
                error,
                exception,
                HttpStatus.BAD_REQUEST
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                error.code(),
                error.message(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalStateException(
            IllegalStateException exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError error = resolveSafeError(
                exception.getMessage(),
                "error.conflict",
                locale
        );

        logClientError(
                request,
                error,
                exception,
                HttpStatus.CONFLICT
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                error.code(),
                error.message(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError mainError = resolveSafeError(
                "validation.failed",
                "validation.failed",
                locale
        );

        List<ApiFieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> toApiFieldError(fieldError, locale))
                .toList();

        logValidationError(
                request,
                mainError,
                exception,
                HttpStatus.BAD_REQUEST,
                fieldErrors.size()
        );

        ApiErrorResponse response = ApiErrorResponse.withFieldErrors(
                mainError.code(),
                mainError.message(),
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError mainError = resolveSafeError(
                "validation.failed",
                "validation.failed",
                locale
        );

        List<ApiFieldError> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(violation -> {
                    ResolvedError fieldError = resolveSafeError(
                            violation.getMessageTemplate(),
                            "validation.invalid",
                            locale
                    );

                    return new ApiFieldError(
                            extractSafeFieldPath(violation.getPropertyPath()),
                            fieldError.code(),
                            fieldError.message()
                    );
                })
                .toList();

        logValidationError(
                request,
                mainError,
                exception,
                HttpStatus.BAD_REQUEST,
                fieldErrors.size()
        );

        ApiErrorResponse response = ApiErrorResponse.withFieldErrors(
                mainError.code(),
                mainError.message(),
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError error = resolveSafeError(
                null,
                "request.body.invalid",
                locale
        );

        logClientErrorWithoutExceptionMessage(
                request,
                error,
                exception,
                HttpStatus.BAD_REQUEST
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                error.code(),
                error.message(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError error = resolveSafeError(
                null,
                "data.integrityViolation",
                locale
        );

        logClientErrorWithoutExceptionMessage(
                request,
                error,
                exception,
                HttpStatus.CONFLICT
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                error.code(),
                error.message(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyLoginAttemptsException(
            TooManyLoginAttemptsException exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError error = resolveSafeError(
                exception.getMessage(),
                "auth.login.tooManyAttempts",
                locale
        );

        logClientError(
                request,
                error,
                exception,
                HttpStatus.TOO_MANY_REQUESTS
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                error.code(),
                error.message(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError error = resolveSafeError(
                exception.getMessage(),
                "error.notFound",
                locale
        );

        logClientError(
                request,
                error,
                exception,
                HttpStatus.NOT_FOUND
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                error.code(),
                error.message(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError error = resolveSafeError(
                exception.getMessage(),
                "error.forbidden",
                locale
        );

        logClientError(
                request,
                error,
                exception,
                HttpStatus.FORBIDDEN
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                error.code(),
                error.message(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception exception,
            HttpServletRequest request,
            Locale locale
    ) {
        ResolvedError error = resolveSafeError(
                null,
                "error.internal",
                locale
        );

        logServerError(
                request,
                error,
                exception,
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                error.code(),
                error.message(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private ApiFieldError toApiFieldError(FieldError fieldError, Locale locale) {
        ResolvedError error = resolveSafeError(
                extractFieldErrorCandidateCode(fieldError),
                "validation.invalid",
                locale
        );

        return new ApiFieldError(
                fieldError.getField(),
                error.code(),
                error.message()
        );
    }

    private ResolvedError resolveSafeError(
            String candidateCode,
            String fallbackCode,
            Locale locale
    ) {
        Locale safeLocale = resolveLocale(locale);

        String normalizedCandidateCode = normalizeCandidateCode(candidateCode);

        if (isValidMessageKey(normalizedCandidateCode)) {
            try {
                String message = messageSource.getMessage(normalizedCandidateCode, null, safeLocale);
                return new ResolvedError(normalizedCandidateCode, message);
            } catch (NoSuchMessageException ignored) {
                // Fallback below.
            }
        }

        String fallbackMessage = messageSource.getMessage(
                fallbackCode,
                null,
                fallbackCode,
                safeLocale
        );

        return new ResolvedError(fallbackCode, fallbackMessage);
    }

    private String normalizeCandidateCode(String candidateCode) {
        if (candidateCode == null || candidateCode.isBlank()) {
            return null;
        }

        String trimmedCode = candidateCode.trim();

        if (trimmedCode.startsWith("{") && trimmedCode.endsWith("}") && trimmedCode.length() > 2) {
            return trimmedCode.substring(1, trimmedCode.length() - 1);
        }

        return trimmedCode;
    }

    private boolean isValidMessageKey(String code) {
        return code != null
                && !code.isBlank()
                && MESSAGE_KEY_PATTERN.matcher(code).matches();
    }

    private void logClientError(
            HttpServletRequest request,
            ResolvedError error,
            Exception exception,
            HttpStatus status
    ) {
        log.warn(
                "Handled client error: status={}, code={}, method={}, path={}, exception={}, message={}",
                status.value(),
                error.code(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName(),
                safeLogMessage(exception)
        );
    }

    private void logValidationError(
            HttpServletRequest request,
            ResolvedError error,
            Exception exception,
            HttpStatus status,
            int fieldErrorCount
    ) {
        log.warn(
                "Handled validation error: status={}, code={}, method={}, path={}, exception={}, fieldErrors={}",
                status.value(),
                error.code(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName(),
                fieldErrorCount
        );
    }

    private void logServerError(
            HttpServletRequest request,
            ResolvedError error,
            Exception exception,
            HttpStatus status
    ) {
        log.error(
                "Unhandled server error: status={}, code={}, method={}, path={}, exception={}, message={}",
                status.value(),
                error.code(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName(),
                safeLogMessage(exception),
                exception
        );
    }

    private String safeLogMessage(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return "";
        }

        String sanitizedMessage = message
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();

        if (sanitizedMessage.length() <= MAX_LOG_MESSAGE_LENGTH) {
            return sanitizedMessage;
        }

        return sanitizedMessage.substring(0, MAX_LOG_MESSAGE_LENGTH) + "...";
    }

    private String extractSafeFieldPath(Path propertyPath) {
        if (propertyPath == null) {
            return ROOT_FIELD;
        }

        List<String> fieldParts = new ArrayList<>();

        for (Path.Node node : propertyPath) {
            if (node.getKind() != ElementKind.PROPERTY) {
                continue;
            }

            String name = node.getName();

            if (isSafeFieldName(name)) {
                fieldParts.add(name);
            }
        }

        if (fieldParts.isEmpty()) {
            return ROOT_FIELD;
        }

        return String.join(".", fieldParts);
    }

    private boolean isSafeFieldName(String fieldName) {
        return fieldName != null
                && fieldName.matches("[a-zA-Z][a-zA-Z0-9_]*");
    }

    private String extractFieldErrorCandidateCode(FieldError fieldError) {
        try {
            ConstraintViolation<?> violation = fieldError.unwrap(ConstraintViolation.class);
            return violation.getMessageTemplate();
        } catch (IllegalArgumentException exception) {
            return fieldError.getDefaultMessage();
        }
    }

    private Locale resolveLocale(Locale locale) {
        if (locale != null) {
            return locale;
        }

        Locale contextLocale = LocaleContextHolder.getLocale();

        if (contextLocale != null) {
            return contextLocale;
        }

        return Locale.ENGLISH;
    }

    private void logClientErrorWithoutExceptionMessage(
            HttpServletRequest request,
            ResolvedError error,
            Exception exception,
            HttpStatus status
    ) {
        log.warn(
                "Handled client error: status={}, code={}, method={}, path={}, exception={}",
                status.value(),
                error.code(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName()
        );
    }

    private record ResolvedError(
            String code,
            String message
    ) {
    }
}