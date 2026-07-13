package cn.lotterydcb.prediction;

import java.util.List;

public record CoverageInfo(
        List<Integer> redPool,
        int uniqueRedCount,
        int coveredPairCount,
        int possiblePairCount,
        double pairCoverageRatio,
        String description
) {

}
