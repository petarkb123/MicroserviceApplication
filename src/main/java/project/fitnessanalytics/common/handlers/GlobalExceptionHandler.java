package project.fitnessanalytics.common.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import project.fitnessanalytics.common.exception.ResourceNotFoundException;
import project.fitnessanalytics.common.exception.UnauthorizedOperationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ModelAndView handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(HttpStatus.BAD_REQUEST);
        modelAndView.addObject("status", HttpStatus.BAD_REQUEST.value());
        modelAndView.addObject("error", "Validation Failed");
        modelAndView.addObject("message", "Invalid request parameters");
        modelAndView.addObject("errors", errors);
        modelAndView.addObject("timestamp", Instant.now());
        
        return modelAndView;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(HttpStatus.BAD_REQUEST);
        modelAndView.addObject("status", HttpStatus.BAD_REQUEST.value());
        modelAndView.addObject("error", "Bad Request");
        modelAndView.addObject("message", ex.getMessage());
        modelAndView.addObject("timestamp", Instant.now());
        
        return modelAndView;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView handleResponseStatusException(ResponseStatusException ex) {
        log.warn("Response status exception: {}", ex.getMessage());
        
        HttpStatus httpStatus = HttpStatus.resolve(ex.getStatusCode().value());
        String errorPhrase = httpStatus != null ? httpStatus.getReasonPhrase() : "Error";
        
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(ex.getStatusCode());
        modelAndView.addObject("status", ex.getStatusCode().value());
        modelAndView.addObject("error", errorPhrase);
        modelAndView.addObject("message", ex.getReason() != null ? ex.getReason() : errorPhrase);
        modelAndView.addObject("timestamp", Instant.now());
        
        return modelAndView;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        modelAndView.addObject("status", HttpStatus.NOT_FOUND.value());
        modelAndView.addObject("error", "Resource Not Found");
        modelAndView.addObject("message", ex.getMessage());
        modelAndView.addObject("timestamp", Instant.now());
        
        return modelAndView;
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ModelAndView handleUnauthorizedOperation(UnauthorizedOperationException ex) {
        log.warn("Unauthorized operation: {}", ex.getMessage());
        
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(HttpStatus.FORBIDDEN);
        modelAndView.addObject("status", HttpStatus.FORBIDDEN.value());
        modelAndView.addObject("error", "Forbidden");
        modelAndView.addObject("message", ex.getMessage());
        modelAndView.addObject("timestamp", Instant.now());
        
        return modelAndView;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        modelAndView.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        modelAndView.addObject("error", "Internal Server Error");
        modelAndView.addObject("message", "An unexpected error occurred");
        modelAndView.addObject("timestamp", Instant.now());
        
        return modelAndView;
    }
}
