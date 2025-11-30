package project.fitnessanalytics.common.handlers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    void handleIllegalArgument_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");
        ModelAndView modelAndView = globalExceptionHandler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, modelAndView.getStatus());
        assertEquals("error", modelAndView.getViewName());
        assertTrue(modelAndView.getModel().containsKey("message"));
        assertEquals("Invalid argument", modelAndView.getModel().get("message"));
        assertEquals(400, modelAndView.getModel().get("status"));
        assertEquals("Bad Request", modelAndView.getModel().get("error"));
    }

    @Test
    void handleResponseStatusException_returnsCorrectStatusAndMessage() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden access");
        ModelAndView modelAndView = globalExceptionHandler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.FORBIDDEN, modelAndView.getStatus());
        assertEquals("error", modelAndView.getViewName());
        assertTrue(modelAndView.getModel().containsKey("message"));
        assertEquals("Forbidden access", modelAndView.getModel().get("message"));
        assertEquals(403, modelAndView.getModel().get("status"));
    }

    @Test
    void handleGenericException_returnsInternalServerError() {
        Exception ex = new Exception("Something went wrong");
        ModelAndView modelAndView = globalExceptionHandler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, modelAndView.getStatus());
        assertEquals("error", modelAndView.getViewName());
        assertTrue(modelAndView.getModel().containsKey("message"));
        assertEquals("An unexpected error occurred", modelAndView.getModel().get("message"));
        assertEquals(500, modelAndView.getModel().get("status"));
        assertEquals("Internal Server Error", modelAndView.getModel().get("error"));
    }
}

