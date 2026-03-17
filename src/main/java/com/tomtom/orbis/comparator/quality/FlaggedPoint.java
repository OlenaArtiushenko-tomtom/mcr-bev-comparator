package com.tomtom.orbis.comparator.quality;

import com.tomtom.orbis.comparator.model.AddressPoint;

import java.util.EnumSet;
import java.util.Set;

public class FlaggedPoint {
    private final AddressPoint point;
    private final EnumSet<QualityFlag> flags = EnumSet.noneOf(QualityFlag.class);

    public FlaggedPoint(AddressPoint point) {
        this.point = point;
    }

    public AddressPoint getPoint() { return point; }
    public Set<QualityFlag> getFlags() { return flags; }
    public void addFlag(QualityFlag flag) { flags.add(flag); }

    public Severity getWorstSeverity() {
        if (flags.isEmpty()) return null;
        for (QualityFlag f : flags) {
            if (f == QualityFlag.OUTSIDE_TILE) return Severity.CRITICAL;
        }
        for (QualityFlag f : flags) {
            if (f.name().startsWith("MISSING_") || f.name().startsWith("DUPLICATE_")
                    || f == QualityFlag.INVALID_HOUSENUMBER_FORMAT
                    || f == QualityFlag.INVALID_POSTCODE_FORMAT
                    || f == QualityFlag.ENCODING_ISSUE
                    || f == QualityFlag.MCR_UNMATCHED) return Severity.WARNING;
        }
        return Severity.INFO;
    }

    public String getFlagsString() {
        return flags.isEmpty() ? "" : String.join(",", flags.stream().map(Enum::name).toList());
    }
}
