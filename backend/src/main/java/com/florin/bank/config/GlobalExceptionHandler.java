package com.florin.bank.config;

import com.florin.bank.auth.InvalidCredentialsException;
import com.florin.bank.customer.CustomerAlreadyExistsException;
import com.florin.bank.transaction.IdempotencyKeyReusedException;
import com.florin.bank.transaction.InsufficientFundsException;
import com.florin.bank.transaction.LimitExceededException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Every error answers as RFC 9457 problem JSON. {@code detail} is a sentence the web app can show as is;
 * {@code code} is stable for programs; validation failures add {@code fields} (field name to message).
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} replaces Spring Boot's own problem-details advice,
 * so framework errors (bad JSON, wrong method, missing parameter) get the same shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = exception.getBody();
        problem.setDetail(fields.isEmpty() ? "Check the details and try again." : fields.values().iterator().next());
        problem.setProperty("code", "VALIDATION_FAILED");
        if (!fields.isEmpty()) {
            problem.setProperty("fields", fields);
        }
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail problem = exception.getBody();
        exception.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(problem::setDetail);
        problem.setProperty("code", "VALIDATION_FAILED");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                "The request body is not valid JSON, or one of its values has the wrong type.");
        problem.setProperty("code", "MALFORMED_REQUEST");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail invalidCredentials(InvalidCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage(), "INVALID_CREDENTIALS");
    }

    @ExceptionHandler(CustomerAlreadyExistsException.class)
    public ProblemDetail customerExists(CustomerAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "EMAIL_TAKEN");
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail insufficientFunds(InsufficientFundsException exception) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), "INSUFFICIENT_FUNDS");
        problem.setProperty("available", exception.getAvailable());
        return problem;
    }

    @ExceptionHandler(LimitExceededException.class)
    public ProblemDetail limitExceeded(LimitExceededException exception) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), exception.getCode());
        problem.setProperty("limit", exception.getLimit());
        problem.setProperty("remaining", exception.getRemaining());
        return problem;
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), "IDEMPOTENCY_KEY_REUSED");
    }

    // Unique, foreign-key and CHECK constraints are the last line of defence against concurrent writes;
    // a violation is a conflict between two requests, not a server fault.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail constraintViolation(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "That change conflicts with one made at the same time. Please try again.",
                "CONFLICT");
    }

    // Only reached after TransactionService has already retried; the client can safely retry too.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> busy(PessimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE,
                        "This account is busy with another payment. Please try again in a moment.", "BUSY"));
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
