package com.agentic.shortener.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to RFC 7807 problem responses. Validation errors of request bodies are
 * handled by Spring's own problem-details support (spring.mvc.problemdetails.enabled=true).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LinkNotFoundException.class)
    ProblemDetail notFound(LinkNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Link not found", ex.getMessage());
    }

    @ExceptionHandler({InvalidUrlException.class, InvalidAliasException.class})
    ProblemDetail badRequest(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(AliasConflictException.class)
    ProblemDetail conflict(AliasConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Alias conflict", ex.getMessage());
    }

    /** Two concurrent requests raced for the same unique code. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail raced(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", "The code was taken by a concurrent request; please retry");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        return p;
    }
}
