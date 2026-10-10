package dev.jimador.decisiondesk;

import java.util.Map;

/** Fixed, fictional road estimates; no maps API or real route is involved. */
public final class SimulatedTrafficProvider {
    public record Estimate(Integer baseMinutes, Integer delayMinutes) {
        public Integer totalMinutes() {
            return baseMinutes == null || delayMinutes == null ? null : baseMinutes + delayMinutes;
        }
    }

    private static final Map<String, Estimate> ESTIMATES = Map.ofEntries(
            Map.entry("calm", new Estimate(30, 10)),
            Map.entry("boundary", new Estimate(30, 30)),
            Map.entry("gust", new Estimate(30, 10)),
            Map.entry("storm", new Estimate(30, 10)),
            Map.entry("lowtide", new Estimate(30, 10)),
            Map.entry("current", new Estimate(30, 10)),
            Map.entry("stale", new Estimate(30, 10)),
            Map.entry("missingcurrent", new Estimate(30, 10)),
            Map.entry("trafficlate", new Estimate(30, 45)),
            Map.entry("trafficunknown", new Estimate(30, null)));

    public Estimate estimate(String caseId) { return ESTIMATES.getOrDefault(caseId, new Estimate(null, null)); }
}
