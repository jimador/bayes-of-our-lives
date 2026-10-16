package dev.jimador.bayes.web;

import dev.jimador.bayes.itinerary.BayesItineraryService;
import dev.jimador.bayes.itinerary.ItineraryRequest;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BayesItineraryController {
    private final BayesItineraryService itineraries;

    public BayesItineraryController(BayesItineraryService itineraries) {
        this.itineraries = itineraries;
    }

    @PostMapping("/itinerary")
    BayesItineraryService.Result itinerary(@RequestBody ItineraryRequest request) {
        request.validateShape();
        return itineraries.plan(request);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadableInput() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Send a complete itinerary request with valid JSON field values.");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException failure) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", failure.getMessage());
    }
    @ExceptionHandler(BayesItineraryService.ModelUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailableModel(BayesItineraryService.ModelUnavailableException failure) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", failure.getMessage());
    }
    @ExceptionHandler(BayesItineraryService.DecisionResponseException.class)
    ResponseEntity<Map<String, String>> failedDecision() {
        return error(HttpStatus.BAD_GATEWAY, "DECISION_FAILED", "The model did not return a complete typed itinerary answer.");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unavailable(Exception failure) {
        return error(HttpStatus.BAD_GATEWAY, "RESEARCH_UNAVAILABLE",
                "The itinerary could not finish. Try again or adjust the city or starting location.");
    }
    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
