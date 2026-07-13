package cn.lotterydcb.prediction;

import java.util.List;

public record TicketMetrics(
        int sum,
        int oddCount,
        int evenCount,
        int smallCount,
        int largeCount,
        List<Integer> zoneCounts,
        int primeCount,
        int compositeCount,
        int neutralCount,
        List<Integer> gaps,
        int span,
        int maxGap,
        int consecutivePairCount,
        int maxConsecutiveRun,
        int acValue,
        int distinctTailCount,
        int maxSameTailCount,
        List<String> regularPatternFlags
) {

}
