package com.appcontrol.panel;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> bad(Exception e) { return ResponseEntity.badRequest().body(Map.of("error",e instanceof IllegalArgumentException ? e.getMessage():"Invalid request fields")); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> unavailable(IllegalStateException e) { return ResponseEntity.status(503).body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> other(Exception e) {
        LoggerFactory.getLogger(ApiErrors.class).error("API request failed: {}",e.getClass().getSimpleName());
        return ResponseEntity.status(500).body(Map.of("error","Request failed. Check server configuration and logs."));
    }
}
