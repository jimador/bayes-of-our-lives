package dev.jimador.bayes.itinerary;

import static dev.jimador.bayes.itinerary.BayesPrompts.DISCOVERY_TASK;
import static dev.jimador.bayes.itinerary.BayesPrompts.DRAFT_TASK;
import static dev.jimador.bayes.itinerary.BayesPrompts.PLANNING_RULES;
import static dev.jimador.bayes.itinerary.BayesPrompts.REFINE_TASK;
import static dev.jimador.bayes.itinerary.BayesPrompts.RESEARCH_RULES;
import static dev.jimador.bayes.itinerary.BayesPrompts.RESEARCH_TASK;
import static dev.jimador.bayes.itinerary.BayesPrompts.UNTRUSTED_EVIDENCE;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.support.InvalidLlmReturnFormatException;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.prompt.PromptContributor;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.jimador.bayes.provider.DayGuideTools;
import dev.jimador.bayes.provider.PageEvidence;
import dev.jimador.bayes.provider.Place;
import dev.jimador.bayes.provider.Places;
import dev.jimador.bayes.provider.PublicPages;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

@Agent(
        description =
                "Discover and research real places, reason over a timed city outing, and evaluate"
                        + " sourced alternatives")
public class BayesAgent {

    private static final Logger LOG = LoggerFactory.getLogger(BayesAgent.class);

    /** Every venue the map searches found, before any model has chosen among them. */
    public record MappedCandidates(List<Place> places) implements PromptContributor {

        @Override
        public String contribution() {
            return "Mapped candidates\n"
                    + places.stream()
                            .map(
                                    p ->
                                            "- placeId %s: %s (%s). Tags: %s"
                                                    .formatted(
                                                            p.id(),
                                                            p.name(),
                                                            p.category(),
                                                            tags(p.tags())))
                            .collect(Collectors.joining("\n"));
        }
    }

    public record Discovery(
            @JsonPropertyDescription(
                            "Place IDs copied exactly from the candidate list, such as N123 or"
                                    + " W456")
                    List<String> placeIds,
            @JsonPropertyDescription("One sentence on why these venues suit the outing")
                    String reason) {}

    /** Map tags and retrieved pages for each venue discovery selected. */
    public record RetrievedEvidence(List<DayGuideTools.Evidence> places)
            implements PromptContributor {

        @Override
        public String contribution() {
            return "Evidence for each selected venue\n"
                    + places.stream()
                            .map(RetrievedEvidence::venue)
                            .collect(Collectors.joining("\n\n"));
        }

        private static String venue(DayGuideTools.Evidence evidence) {
            var place = evidence.place();
            var pages =
                    evidence.pages().isEmpty()
                            ? "- No pages were retrieved."
                            : evidence.pages().stream()
                                    .map(RetrievedEvidence::page)
                                    .collect(Collectors.joining("\n"));
            return """
            Venue %s: %s (%s)
            - Map tags, source %s: %s
            %s\
            """
                    .formatted(
                            place.id(),
                            place.name(),
                            place.category(),
                            place.id(),
                            tags(place.tags()),
                            pages);
        }

        private static String page(PageEvidence page) {
            return "- Page %s (%s), %s: %s"
                    .formatted(page.id(), page.status(), page.url(), page.excerpt());
        }
    }

    public record TravelPlan(
            String city,
            String date,
            String status,
            String selectedAlternative,
            List<Itinerary.Alternative> alternatives,
            ItineraryDecisions.Answer review,
            ItineraryMemories.Accepted memory,
            List<String> researchSources,
            String retrievedUtc,
            List<ResearchFindings.PlaceView> researchedPlaces,
            String destinationTimezone) {}

    private final ItineraryMemories memories;
    private final Places places;
    private final PublicPages pages;
    private final ItineraryDecisions decisions;
    private final LlmOptions researcher;
    private final LlmOptions planner;
    private final MealTimes meals;

    public BayesAgent(
            ItineraryMemories memories,
            Places places,
            PublicPages pages,
            ItineraryDecisions decisions,
            @Qualifier("researcherLlm") LlmOptions researcher,
            @Qualifier("plannerLlm") LlmOptions planner,
            MealTimes meals) {
        this.memories = memories;
        this.places = places;
        this.pages = pages;
        this.decisions = decisions;
        this.researcher = researcher;
        this.planner = planner;
        this.meals = meals;
    }

    public ItineraryContext prepare(ItineraryRequest request) {
        request.validateShape();
        if (!request.questions(meals).isEmpty())
            throw new IllegalArgumentException("Outing requirements are incomplete");
        var memory = memories.rememberForThisOuting(request);
        var location = places.locate(request.city(), request.startingLocation());
        var brief = ItineraryBrief.from(request, memory, meals, location.timezone());
        ItineraryValidation.date(brief, location.timezone());
        var tools = new DayGuideTools(places, pages, location, brief.cuisineLikes());
        return new ItineraryContext(
                brief,
                memory,
                tools,
                location.timezone(),
                request.hasDietaryNeed(),
                request.engine());
    }

    @Action
    public MappedCandidates gatherCandidates(ItineraryContext outing) {
        var tools = outing.tools();
        tools.search("museum", "");
        tools.search("park", "");
        var requested = outing.brief().requestedStops();
        if (requested.contains("breakfast")) tools.search("breakfast", "");
        if (requested.contains("coffee")) tools.search("cafe", "");
        if (requested.contains("lunch") || requested.contains("dinner")) {
            tools.search("restaurant", "");
            outing.brief().cuisineLikes().stream()
                    .limit(2)
                    .forEach(cuisine -> tools.search("restaurant", cuisine));
        }
        return new MappedCandidates(tools.candidates());
    }

    @Action
    public Discovery discoverPlaces(
            ItineraryContext outing, MappedCandidates candidates, OperationContext context) {
        if (candidates.places().isEmpty())
            throw new IllegalStateException("No mapped candidates were retrieved");
        Discovery discovered;
        try {
            discovered =
                    context.ai()
                            .withLlm(researcher)
                            .withPromptElements(UNTRUSTED_EVIDENCE, outing.brief(), candidates)
                            .createObject(DISCOVERY_TASK, Discovery.class);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Mapped discovery could not complete");
        }
        return checked(discovered, candidates);
    }

    private static Discovery checked(Discovery discovered, MappedCandidates candidates) {
        var ids = discovered == null ? null : discovered.placeIds();
        if (ids == null
                || ids.isEmpty()
                || ids.size() > 8
                || ids.stream().distinct().count() != ids.size())
            throw new IllegalStateException("Discovery returned an invalid candidate set");
        var known = candidates.places().stream().map(Place::id).toList();
        if (!known.containsAll(ids)) {
            LOG.info(
                    "stage=discovery unknown={} known={}",
                    ids.stream().filter(id -> !known.contains(id)).toList(),
                    known.size());
            throw new IllegalStateException(
                    "Discovery selected a place outside the mapped candidates");
        }
        return discovered;
    }

    @Action
    public RetrievedEvidence retrieveEvidence(ItineraryContext outing, Discovery discovery) {
        return new RetrievedEvidence(
                discovery.placeIds().stream().map(outing.tools()::readPlace).toList());
    }

    @Action
    public ResearchFindings researchPlaces(
            ItineraryContext outing, RetrievedEvidence evidence, OperationContext context) {
        var selected = evidence.places().stream().map(e -> e.place().id()).toList();
        ResearchFindings findings;
        try {
            findings =
                    context.ai()
                            .withLlm(researcher)
                            .withToolObject(outing.tools())
                            .withPromptElements(
                                    UNTRUSTED_EVIDENCE, RESEARCH_RULES, outing.brief(), evidence)
                            .createObject(RESEARCH_TASK, ResearchFindings.class);
        } catch (InvalidLlmReturnFormatException failure) {
            LOG.info("stage=research issue=MODEL_OUTPUT_FORMAT count={}", selected.size());
            return ResearchFindings.unavailable(
                    selected, ResearchFindings.Issue.MODEL_OUTPUT_FORMAT);
        }
        return ItineraryValidation.research(findings, selected, outing);
    }

    @Action
    public Itinerary.Draft draftDay(
            ItineraryContext outing,
            ResearchFindings research,
            RetrievedEvidence evidence,
            OperationContext context) {
        return context.ai()
                .withLlm(planner)
                .withToolObject(outing.tools())
                .withPromptElements(
                        UNTRUSTED_EVIDENCE,
                        PLANNING_RULES,
                        outing.brief(),
                        evidence,
                        research.asPrompt())
                .createObject(DRAFT_TASK, Itinerary.Draft.class);
    }

    @Action
    public Itinerary.ReviewedDraft reviewDraft(
            ItineraryContext outing, ResearchFindings research, Itinerary.Draft draft) {
        var checked = ItineraryValidation.plans(draft, research, outing);
        LOG.info(
                "stage=plan-review invalid={} unresolved={}",
                count(checked, "invalid"),
                count(checked, "unresolved"));
        return new Itinerary.ReviewedDraft(draft, checked.alternatives());
    }

    @Action
    public Itinerary finalizeDay(
            ItineraryContext outing,
            ResearchFindings research,
            RetrievedEvidence evidence,
            Itinerary.ReviewedDraft reviewed,
            OperationContext context) {
        boolean refine =
                reviewed.alternatives().stream().anyMatch(a -> !a.status().equals("usable"));
        LOG.info("stage=plan-refinement requested={}", refine);
        if (!refine) return ItineraryValidation.plans(reviewed.draft(), research, outing);
        var refined =
                context.ai()
                        .withLlm(planner)
                        .withToolObject(outing.tools())
                        .withPromptElements(
                                UNTRUSTED_EVIDENCE,
                                PLANNING_RULES,
                                outing.brief(),
                                evidence,
                                research.asPrompt(),
                                reviewed)
                        .createObject(REFINE_TASK, Itinerary.Draft.class);
        return ItineraryValidation.plans(refined, research, outing);
    }

    /**
     * The outing's engine is a role name, so the platform's registry decides which service answers.
     */
    @Action
    public ItineraryDecisions.Answer decideDay(
            ItineraryContext outing, Itinerary itinerary, OperationContext context) {
        var role = outing.decisionRole();
        return decisions.evaluate(
                DecisionBrief.from(outing.brief(), itinerary.alternatives()),
                itinerary.alternatives(),
                context.ai().decisions().byRole(role),
                context.ai().classifications().byRole(role));
    }

    @Action
    @AchievesGoal(
            description =
                    "Return a useful attributed city outline and show material questions separately"
                            + " from advisory notes")
    public TravelPlan completeItinerary(
            ItineraryContext outing,
            ResearchFindings research,
            Itinerary itinerary,
            ItineraryDecisions.Answer review) {
        var selected =
                itinerary.alternatives().stream()
                        .filter(
                                a ->
                                        a.id().equals(review.choice())
                                                && !a.status().equals("invalid"))
                        .findFirst()
                        .orElse(null);
        String status =
                review.failed()
                        ? "decision_failed"
                        : selected == null ? "needs_research" : selected.status();
        var researched =
                research.places().stream()
                        .map(
                                finding -> {
                                    var evidence = outing.tools().evidence(finding.placeId());
                                    return new ResearchFindings.PlaceView(
                                            evidence.place(), finding, evidence.pages());
                                })
                        .toList();
        return new TravelPlan(
                outing.brief().city(),
                outing.brief().date().toString(),
                status,
                selected == null ? null : selected.id(),
                itinerary.alternatives(),
                review,
                outing.memory(),
                outing.tools().sourceUrls(),
                Instant.now().toString(),
                researched,
                outing.timezone());
    }

    private static String tags(Map<String, String> tags) {
        if (tags.isEmpty()) return "none";
        return tags.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    private static long count(Itinerary itinerary, String status) {
        return itinerary.alternatives().stream().filter(a -> a.status().equals(status)).count();
    }
}
