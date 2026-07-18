package dev.automata.automata.utils;


import lombok.Getter;

@Getter
public class FeatureDisabledException extends RuntimeException {
    private final Feature feature;

    public FeatureDisabledException(Feature feature) {
        super("Feature '%s' is currently disabled".formatted(feature));
        this.feature = feature;
    }

}
