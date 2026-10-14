package dev.jimador.bayes.provider;

import java.util.Map;

/** Public map evidence. Coordinates and lookup URLs stay in the provider. */
public record Place(String id, String name, String category, String sourceUrl, Map<String, String> tags) {}
