package com.tomtom.orbis.comparator.quality.check;

import com.tomtom.orbis.comparator.quality.CheckContext;
import com.tomtom.orbis.comparator.quality.DimensionResult;
import com.tomtom.orbis.comparator.quality.FlaggedPoint;

import java.util.List;

public interface QualityCheck {
    String dimensionName();
    double weight();
    DimensionResult run(List<FlaggedPoint> points, CheckContext context);
}
