package com.tomtom.orbis.comparator.quality;

public record Anomaly(
        Severity severity,
        String dimension,
        String code,
        String message,
        String pointId,
        Double lat,
        Double lon
) {}
