package dev.jimador.bayes.itinerary;

import dev.jimador.bayes.provider.DayGuideTools;

/**
 * Internal action state. Serialize only brief/findings explicitly when prompting.
 *
 * @param decisionRole the decision and classification role that answers this outing's questions
 */
public record ItineraryContext(ItineraryBrief brief, ItineraryMemories.Accepted memory,
                               DayGuideTools tools, String timezone, boolean privateRestrictionCheck,
                               String decisionRole) {}
