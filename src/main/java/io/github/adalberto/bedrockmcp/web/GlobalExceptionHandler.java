package io.github.adalberto.bedrockmcp.web;

import io.github.adalberto.bedrockmcp.catalog.CatalogService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import software.amazon.awssdk.core.exception.SdkException;

/** RFC 9457 problem details. Never leaks stack traces or model/AWS internals to clients. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CatalogService.NotFoundException.class)
    ProblemDetail notFound(CatalogService.NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CatalogService.BusinessRuleException.class)
    ProblemDetail businessRule(CatalogService.BusinessRuleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    @ExceptionHandler(SdkException.class)
    ProblemDetail aws(SdkException ex) {
        log.error("AWS call failed", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The AI service is temporarily unavailable. Please retry.");
        pd.setTitle("Upstream AI service error");
        return pd;
    }
}
