package com.amit.jobagent.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidWebhookSecretException.class)
    ProblemDetail invalidSecret(InvalidWebhookSecretException ex, HttpServletRequest request) { return problem(HttpStatus.FORBIDDEN, "Webhook authentication failed", ex.getMessage(), request); }
    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missingHeader(MissingRequestHeaderException ex, HttpServletRequest request) { return problem(HttpStatus.UNAUTHORIZED, "Required header missing", "A required request header was not supplied", request); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var detail=problem(HttpStatus.BAD_REQUEST, "Validation failed", "The request contains invalid values", request);
        var fields=new LinkedHashMap<String,String>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        detail.setProperty("fieldErrors", fields); return detail;
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformed(HttpMessageNotReadableException ex, HttpServletRequest request) { return problem(HttpStatus.BAD_REQUEST, "Malformed request", "The request body could not be read", request); }
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException ex, HttpServletRequest request) { return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), request); }
    @ExceptionHandler({ConflictException.class, ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail conflict(Exception ex, HttpServletRequest request) { return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with the current resource state", request); }
    @ExceptionHandler(DomainValidationException.class)
    ProblemDetail domainValidation(DomainValidationException ex, HttpServletRequest request) {
        var status=ex instanceof PublishValidationException ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_REQUEST;
        var detail=problem(status, status==HttpStatus.UNPROCESSABLE_ENTITY ? "Profile cannot be published" : "Invalid request", ex.getMessage(), request);
        if (!ex.getFieldErrors().isEmpty()) detail.setProperty("fieldErrors", ex.getFieldErrors()); return detail;
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) { return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "The uploaded document exceeds the configured size limit", request); }
    @ExceptionHandler(DocumentTooLargeException.class)
    ProblemDetail tooLarge(DocumentTooLargeException ex, HttpServletRequest request) { return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", ex.getMessage(), request); }
    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    ProblemDetail unsupported(UnsupportedDocumentTypeException ex, HttpServletRequest request) { return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", ex.getMessage(), request); }
    @ExceptionHandler(StorageUnavailableException.class)
    ProblemDetail storageUnavailable(StorageUnavailableException ex, HttpServletRequest request) { return problem(HttpStatus.SERVICE_UNAVAILABLE, "Object storage unavailable", "The document store is temporarily unavailable", request); }
    @ExceptionHandler(ExternalSourceUnavailableException.class)
    ProblemDetail sourceUnavailable(ExternalSourceUnavailableException ex, HttpServletRequest request) {
        var detail=problem(HttpStatus.SERVICE_UNAVAILABLE, "Job source unavailable", ex.getMessage(), request);
        detail.setProperty("errorCode", ex.getSafeCode()); return detail;
    }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) { return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred", request); }
    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        var problem=ProblemDetail.forStatusAndDetail(status, detail); problem.setTitle(title); problem.setInstance(URI.create(request.getRequestURI())); return problem;
    }
}
