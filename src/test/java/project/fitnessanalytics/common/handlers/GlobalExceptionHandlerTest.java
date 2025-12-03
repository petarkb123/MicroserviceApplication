package project.fitnessanalytics.common.handlers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private HttpServletRequest request;

    @Test
    void handleIllegalArgument_returnsBadRequestJson() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");
        when(request.getRequestURI()).thenReturn("/test/path");

        ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleIllegalArgument(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertTrue(body != null && body.containsKey("message"));
        assertEquals(400, body.get("status"));
        assertEquals("Bad Request", body.get("error"));
        assertEquals("Invalid argument", body.get("message"));
        assertEquals("/test/path", body.get("path"));
    }

    @Test
    void handleResponseStatusException_returnsCorrectStatusAndMessageJson() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden access");
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleResponseStatusException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertTrue(body != null && body.containsKey("message"));
        assertEquals(403, body.get("status"));
        assertEquals("Forbidden", body.get("error"));
        assertEquals("Forbidden access", body.get("message"));
        assertEquals("/api/test", body.get("path"));
    }

    @Test
    void handleGenericException_returnsInternalServerErrorJson() {
        Exception ex = new Exception("Something went wrong");
        when(request.getRequestURI()).thenReturn("/any");

        ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertTrue(body != null && body.containsKey("message"));
        assertEquals(500, body.get("status"));
        assertEquals("Internal Server Error", body.get("error"));
        assertEquals("An unexpected error occurred", body.get("message"));
        assertEquals("/any", body.get("path"));
    }
}

