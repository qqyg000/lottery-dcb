package cn.lotterydcb.prediction;

import java.util.ArrayList;
import java.util.List;

public final class ConstraintEvaluator {

    private ConstraintEvaluator() {
    }

    public static ConstraintEvaluation evaluate(TicketMetrics metrics, StrategyParameters strategy) {
        List<String> reasons = new ArrayList<>();
        require(metrics.consecutivePairCount() <= strategy.getMaxConsecutivePairs(), "连号对数超限", reasons);
        require(metrics.maxConsecutiveRun() <= strategy.getMaxConsecutiveRun(), "连续段长度超限", reasons);
        require(between(metrics.oddCount(), strategy.getMinOddCount(), strategy.getMaxOddCount()), "奇偶比例不平衡", reasons);
        require(between(metrics.smallCount(), strategy.getMinSmallCount(), strategy.getMaxSmallCount()), "大小号比例不平衡", reasons);
        require(between(metrics.sum(), strategy.getMinSum(), strategy.getMaxSum()), "和值超出范围", reasons);
        require(metrics.zoneCounts().stream().allMatch(count -> between(
                count,
                strategy.getMinZoneCount(),
                strategy.getMaxZoneCount()
        )), "三区分布不均衡", reasons);
        require(between(metrics.primeCount(), strategy.getMinPrimeCount(), strategy.getMaxPrimeCount()), "质合搭配不符合范围", reasons);
        require(metrics.compositeCount() >= 1, "至少需要一个合数", reasons);
        require(metrics.span() >= strategy.getMinSpan(), "号码跨度过小", reasons);
        require(metrics.maxGap() <= strategy.getMaxGap(), "相邻号码最大间隔超限", reasons);
        require(between(metrics.acValue(), strategy.getMinAcValue(), strategy.getMaxAcValue()), "AC 值超出范围", reasons);
        require(metrics.distinctTailCount() >= strategy.getMinDistinctTails(), "尾数过于集中", reasons);
        require(metrics.maxSameTailCount() <= strategy.getMaxSameTailCount(), "同尾号码过多", reasons);
        if (strategy.isAvoidRegularPatterns()) {
            require(metrics.regularPatternFlags().isEmpty(), "存在明显规律图案", reasons);
        }
        return new ConstraintEvaluation(reasons.isEmpty(), List.copyOf(reasons));
    }

    public static double score(TicketMetrics metrics) {
        double score = 100.0;
        score -= Math.abs(metrics.oddCount() - 3) * 3.0;
        score -= Math.abs(metrics.smallCount() - 3) * 3.0;
        score -= Math.abs(metrics.sum() - 102) * 0.16;
        score -= metrics.zoneCounts().stream().mapToInt(count -> Math.abs(count - 2)).sum() * 2.2;
        score -= Math.abs(metrics.primeCount() - 2) * 2.0;
        score -= Math.abs(metrics.acValue() - 8) * 1.4;
        score -= Math.abs(metrics.distinctTailCount() - 5) * 1.5;
        score -= metrics.consecutivePairCount() * 1.5;
        score -= Math.max(0, metrics.maxGap() - 9) * 0.8;
        return Math.max(0.0, score);
    }

    private static boolean between(int value, int min, int max) {
        return value >= min && value <= max;
    }

    private static void require(boolean condition, String reason, List<String> reasons) {
        if (!condition) {
            reasons.add(reason);
        }
    }

}
