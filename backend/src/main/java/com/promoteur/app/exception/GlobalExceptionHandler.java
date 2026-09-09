package com.promoteur.app.exception;

import com.promoteur.app.shared.MessageService;
import com.promoteur.app.shared.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized REST exception handling for the application API.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageService messageService;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(final ResourceNotFoundException ex) {
        return this.buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(final MethodArgumentNotValidException ex) {
        final Map<String, String> errors = new LinkedHashMap<>();
        for (final FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation error");
        body.put("details", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(final IllegalArgumentException ex) {
        return this.buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * A concurrent write won the race (CONC-01). 409 tells the console to reload rather than
     * silently overwriting the other operation.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLocking(final OptimisticLockingFailureException ex) {
        LOGGER.warn("Optimistic locking conflict", ex);
        return this.buildResponse(HttpStatus.CONFLICT, this.messageService.get("error.optimisticLock"));
    }

    /**
     * The servlet layer rejects the upload before the service sees it (FE-05).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(final MaxUploadSizeExceededException ex) {
        return this.buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, this.messageService.get("error.uploadTooLarge"));
    }

    /**
     * A duplicate reference or a broken foreign key (API-02). Without this branch the failure
     * fell through to {@link #handleGeneric} and answered 500 « Unexpected server error », which
     * the console could neither explain nor act on.
     *
     * <p>409 rather than 400: the payload is well formed, it just contradicts data already
     * stored. The violated constraint is logged, never returned — a constraint name tells the
     * caller about the schema and helps nobody using the application.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(final DataIntegrityViolationException ex) {
        LOGGER.warn("Data integrity violation", ex);
        return this.buildResponse(HttpStatus.CONFLICT, this.messageService.get("error.dataIntegrity"));
    }

    /**
     * A query parameter or path variable that cannot be converted (API-02): {@code ownerType=BOGUS}
     * on an enum parameter, or a non-numeric identifier. Both answered 500 before.
     *
     * <p>The message names the parameter but never the value received: echoing user input back
     * into a message is how a reflected payload reaches the console's toast.</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(final MethodArgumentTypeMismatchException ex) {
        return this.buildResponse(HttpStatus.BAD_REQUEST,
                this.messageService.get("error.parameterTypeMismatch", ex.getName()));
    }

    /**
     * A body Jackson cannot read: malformed JSON, or a value of the wrong shape (API-02). The
     * parse error itself is logged rather than returned, since it quotes the payload.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(final HttpMessageNotReadableException ex) {
        LOGGER.warn("Unreadable request body", ex);
        return this.buildResponse(HttpStatus.BAD_REQUEST, this.messageService.get("error.malformedRequestBody"));
    }

    /**
     * A required query parameter that was not sent (API-02): {@code /api/search} without
     * {@code q}, or an export without {@code year}. Answered 500 before, which told the caller
     * nothing about what was missing.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(
            final MissingServletRequestParameterException ex) {
        return this.buildResponse(HttpStatus.BAD_REQUEST,
                this.messageService.get("error.missingParameter", ex.getParameterName()));
    }

    /**
     * A multipart request that arrived without one of its parts (API-02, FE-05): an upload
     * missing its {@code file}. Same defect shape as an unconvertible parameter — a malformed
     * request answering 500 — so it belongs with the branch above rather than with the
     * unexpected failures.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(final MissingServletRequestPartException ex) {
        return this.buildResponse(HttpStatus.BAD_REQUEST,
                this.messageService.get("error.missingRequestPart", ex.getRequestPartName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(final Exception ex) {
        LOGGER.error("Unhandled API exception", ex);
        return this.buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(final HttpStatus status, final String message) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
