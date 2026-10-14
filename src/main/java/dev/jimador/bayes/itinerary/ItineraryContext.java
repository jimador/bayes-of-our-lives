package dev.jimador.bayes.itinerary;

import dev.jimador.bayes.provider.DayGuideTools;

/** Internal action state. Serialize only brief/findings explicitly when prompting. */
public record ItineraryContext(ItineraryBrief brief, ItineraryMemories.Accepted memory,
                               DayGuideTools tools, String timezone, boolean privateRestrictionCheck) {}
