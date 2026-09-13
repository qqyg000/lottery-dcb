package cn.lotterydcb.prediction;

import java.util.List;

/**
 * @param redPool 实际使用或指定的矩阵红球池
 * @param uniqueRedCount 已覆盖的不同红球数量
 * @param coveredPairCount 已覆盖的不同红球二码数量
 * @param possiblePairCount 红球池内所有可能的二码数量
 * @param pairCoverageRatio 池内二码覆盖比例，范围 0 到 1
 * @param description 覆盖口径说明
 * @param coveredTripleCount 已覆盖的不同红球三码数量
 * @param possibleTripleCount 红球池内所有可能的三码数量
 * @param tripleCoverageRatio 池内三码覆盖比例，范围 0 到 1
 * @param uniqueBlueCount 整批投注覆盖的不同蓝球数量，范围 1 到 16
 * @param blueCoverageRatio 不同蓝球数量除以 16，不是整批投注的整体中奖率
 */
public record CoverageInfo(
        List<Integer> redPool,
        int uniqueRedCount,
        int coveredPairCount,
        int possiblePairCount,
        double pairCoverageRatio,
        String description,
        int coveredTripleCount,
        int possibleTripleCount,
        double tripleCoverageRatio,
        int uniqueBlueCount,
        double blueCoverageRatio
) {

}
