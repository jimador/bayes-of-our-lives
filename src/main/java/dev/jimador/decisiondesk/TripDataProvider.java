package dev.jimador.decisiondesk;

import java.time.Instant;

/** Supplies sourced place and forecast leads for an arbitrary destination. */
public interface TripDataProvider {
    LiveCoastalProvider.Search search(String destination, Instant windowStartUtc, String origin);
}
