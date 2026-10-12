package dev.jimador.bays;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BayesGuideController {
    private final BayesGuideService guide;
    public BayesGuideController(BayesGuideService guide) { this.guide = guide; }

    @PostMapping("/guide")
    public BayesGuideService.GuideResult guide(@RequestBody BayesGuideService.GuideQuery query) {
        return guide.guide(query);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException failure) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", failure.getMessage());
    }

    @ExceptionHandler(BayesDecisionServices.ModelUnavailableException.class)
    ResponseEntity<Map<String, String>> modelUnavailable(BayesDecisionServices.ModelUnavailableException failure) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", failure.getMessage());
    }

    @ExceptionHandler(BayesGuideService.DecisionResponseException.class)
    ResponseEntity<Map<String, String>> decisionFailed() {
        return error(HttpStatus.BAD_GATEWAY, "DECISION_FAILED",
                "The selected engine returned an incomplete typed answer. Try again or choose another engine.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unavailable(Exception failure) {
        // Provider and process exceptions can contain request URLs or host paths. Keep them server-side.
        return error(HttpStatus.BAD_GATEWAY, "GUIDE_UNAVAILABLE",
                "The city lookup or guide process could not finish. Try fixed mode or another city.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
