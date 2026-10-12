package dev.jimador.bays;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** CLI surface for two fixed memory profiles or one practical city lookup. */
@Component
public class BaysRunner implements CommandLineRunner {
    private final AgentPlatform platform;
    private final String engine, mode, city, intent, profile, mealPeriod, captureFile;
    private final boolean web;
    private final int availableMinutes;

    public BaysRunner(AgentPlatform platform, @Value("${demo.engine:local}") String engine,
            @Value("${bays.mode:fixed}") String mode, @Value("${bays.city:Harborview}") String city,
            @Value("${bays.intent:sights}") String intent,
            @Value("${bays.meal-period:lunch}") String mealPeriod,
            @Value("${bays.profile:both}") String profile,
            @Value("${bays.capture-file:}") String captureFile,
            @Value("${bays.available-minutes:90}") int availableMinutes,
            @Value("${bays.web:false}") boolean web) {
        this.platform = platform;
        this.engine = engine;
        this.mode = mode;
        this.city = city;
        this.intent = intent;
        this.mealPeriod = mealPeriod;
        this.profile = profile;
        this.captureFile = captureFile;
        this.availableMinutes = availableMinutes;
        this.web = web;
    }
    @Override public void run(String... args) {
        if (web) return;
        if (mode.equals("live") && profile.equals("both"))
            throw new IllegalArgumentException("Select one bays.profile for live lookup");
        if (!captureFile.isBlank() && (!mode.equals("fixed") || profile.equals("both")))
            throw new IllegalArgumentException("Capture requires fixed mode and one profile");
        List<String> profiles = profile.equals("both") ? List.of("art-quiet", "science-lively") : List.of(profile);
        var agent = platform.agents().stream().filter(a -> a.getName().contains("BayesAgent"))
                .findFirst().orElseThrow(() -> new IllegalStateException("BayesAgent was not deployed"));
        for (String chosen : profiles) {
            var request = new BayesAgent.Request(city, intent, availableMinutes, chosen, mode, mealPeriod, engine);
            long started = System.nanoTime();
            var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), request).run();
            double processMs = (System.nanoTime() - started) / 1_000_000.0;
            if (process.getStatus() != AgentProcessStatusCode.COMPLETED)
                throw new IllegalStateException("Bays process: " + process.getStatus() + " " + process.getFailureInfo());
            var plan = process.last(BayesAgent.Plan.class);
            if (!captureFile.isBlank()) writeCapture(plan);
            System.out.println("app=Bays engine=" + engine + " mode=" + mode + " intent=" + intent
                    + " city=" + plan.city() + " availableMinutes=" + availableMinutes + " profile=" + chosen
                    + " status=" + plan.status() + " selectedResearchLead=" + plan.selectedResearchLead()
                    + " ranked=" + plan.rankedOptions() + " processMs=" + processMs
                    + " retrievedUtc=" + plan.retrievedUtc() + " source=" + plan.providerSource());
            System.out.println("dice=" + plan.memoryProvenance());
            for (var answer : plan.assessments()) {
                System.out.println("place=" + answer.option().name() + " source=" + answer.option().sourceUrl()
                        + " oneWayBaseDriveMinutes=" + answer.option().oneWayDriveMinutes()
                        + " hardStatus=" + answer.hardStatus() + " inputSha256=" + sha256(answer.decisionInput()) + " memoryInput={"
                        + answer.decisionInput().replace("\n", "; ") + "}"
                        + " interestMatch=" + answer.interestMatch() + " choice=" + answer.choice()
                        + " rating=" + answer.rating() + " setting=" + answer.setting() + " model=" + answer.model()
                        + " decisionMs=" + answer.decisionNanos() / 1_000_000.0
                        + " classificationMs=" + answer.classificationNanos() / 1_000_000.0);
            }
            System.out.println("hoursAdmissionDietarySafetyAccessibilityUnverified=true liveTrafficUnknown=true"
                    + " fictionalVisitMinutes=true");
            if (mode.equals("live")) System.out.println("placeData=© OpenStreetMap contributors (ODbL) https://www.openstreetmap.org/copyright");
            System.out.println("actions=" + process.getHistory().stream()
                    .map(com.embabel.agent.core.ActionInvocation::getActionName)
                    .collect(java.util.stream.Collectors.joining(" -> ")));
        }
    }

    private void writeCapture(BayesAgent.Plan plan) {
        try {
            var path = Path.of(captureFile);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            var cases = plan.assessments().stream().map(a -> java.util.Map.of(
                    "place", a.option().name(), "hardStatus", a.hardStatus(),
                    "decisionInput", a.decisionInput(), "inputSha256", sha256(a.decisionInput()))).toList();
            var capture = java.util.Map.of(
                    "schema", "bays-decision-input-v1", "intent", plan.intent(),
                    "availableMinutes", plan.availableMinutes(), "profile", plan.profile(),
                    "provider", "synthetic Bays catalog", "memoryProvenance", plan.memoryProvenance(),
                    "memoryFacts", plan.memoryFacts(), "cases", cases);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), capture);
            System.out.println("captureFile=" + path + " cases=" + cases.size());
        } catch (Exception failure) {
            throw new IllegalStateException("Could not write decision input capture", failure);
        }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
