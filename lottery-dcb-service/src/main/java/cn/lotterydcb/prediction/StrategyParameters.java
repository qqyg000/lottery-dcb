package cn.lotterydcb.prediction;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class StrategyParameters {

    @Min(0)
    @Max(5)
    private int maxConsecutivePairs = 1;

    @Min(1)
    @Max(6)
    private int maxConsecutiveRun = 2;

    @Min(0)
    @Max(6)
    private int minOddCount = 2;

    @Min(0)
    @Max(6)
    private int maxOddCount = 4;

    @Min(0)
    @Max(6)
    private int minSmallCount = 2;

    @Min(0)
    @Max(6)
    private int maxSmallCount = 4;

    @Min(21)
    @Max(183)
    private int minSum = 75;

    @Min(21)
    @Max(183)
    private int maxSum = 130;

    @Min(0)
    @Max(6)
    private int minZoneCount = 1;

    @Min(0)
    @Max(6)
    private int maxZoneCount = 3;

    @Min(0)
    @Max(6)
    private int minPrimeCount = 1;

    @Min(0)
    @Max(6)
    private int maxPrimeCount = 3;

    @Min(0)
    @Max(32)
    private int minSpan = 18;

    @Min(1)
    @Max(32)
    private int maxGap = 12;

    @Min(0)
    @Max(10)
    private int minAcValue = 5;

    @Min(0)
    @Max(10)
    private int maxAcValue = 10;

    @Min(1)
    @Max(6)
    private int minDistinctTails = 4;

    @Min(1)
    @Max(6)
    private int maxSameTailCount = 2;

    private boolean avoidRegularPatterns = true;

    public int getMaxConsecutivePairs() {
        return maxConsecutivePairs;
    }

    public void setMaxConsecutivePairs(int maxConsecutivePairs) {
        this.maxConsecutivePairs = maxConsecutivePairs;
    }

    public int getMaxConsecutiveRun() {
        return maxConsecutiveRun;
    }

    public void setMaxConsecutiveRun(int maxConsecutiveRun) {
        this.maxConsecutiveRun = maxConsecutiveRun;
    }

    public int getMinOddCount() {
        return minOddCount;
    }

    public void setMinOddCount(int minOddCount) {
        this.minOddCount = minOddCount;
    }

    public int getMaxOddCount() {
        return maxOddCount;
    }

    public void setMaxOddCount(int maxOddCount) {
        this.maxOddCount = maxOddCount;
    }

    public int getMinSmallCount() {
        return minSmallCount;
    }

    public void setMinSmallCount(int minSmallCount) {
        this.minSmallCount = minSmallCount;
    }

    public int getMaxSmallCount() {
        return maxSmallCount;
    }

    public void setMaxSmallCount(int maxSmallCount) {
        this.maxSmallCount = maxSmallCount;
    }

    public int getMinSum() {
        return minSum;
    }

    public void setMinSum(int minSum) {
        this.minSum = minSum;
    }

    public int getMaxSum() {
        return maxSum;
    }

    public void setMaxSum(int maxSum) {
        this.maxSum = maxSum;
    }

    public int getMinZoneCount() {
        return minZoneCount;
    }

    public void setMinZoneCount(int minZoneCount) {
        this.minZoneCount = minZoneCount;
    }

    public int getMaxZoneCount() {
        return maxZoneCount;
    }

    public void setMaxZoneCount(int maxZoneCount) {
        this.maxZoneCount = maxZoneCount;
    }

    public int getMinPrimeCount() {
        return minPrimeCount;
    }

    public void setMinPrimeCount(int minPrimeCount) {
        this.minPrimeCount = minPrimeCount;
    }

    public int getMaxPrimeCount() {
        return maxPrimeCount;
    }

    public void setMaxPrimeCount(int maxPrimeCount) {
        this.maxPrimeCount = maxPrimeCount;
    }

    public int getMinSpan() {
        return minSpan;
    }

    public void setMinSpan(int minSpan) {
        this.minSpan = minSpan;
    }

    public int getMaxGap() {
        return maxGap;
    }

    public void setMaxGap(int maxGap) {
        this.maxGap = maxGap;
    }

    public int getMinAcValue() {
        return minAcValue;
    }

    public void setMinAcValue(int minAcValue) {
        this.minAcValue = minAcValue;
    }

    public int getMaxAcValue() {
        return maxAcValue;
    }

    public void setMaxAcValue(int maxAcValue) {
        this.maxAcValue = maxAcValue;
    }

    public int getMinDistinctTails() {
        return minDistinctTails;
    }

    public void setMinDistinctTails(int minDistinctTails) {
        this.minDistinctTails = minDistinctTails;
    }

    public int getMaxSameTailCount() {
        return maxSameTailCount;
    }

    public void setMaxSameTailCount(int maxSameTailCount) {
        this.maxSameTailCount = maxSameTailCount;
    }

    public boolean isAvoidRegularPatterns() {
        return avoidRegularPatterns;
    }

    public void setAvoidRegularPatterns(boolean avoidRegularPatterns) {
        this.avoidRegularPatterns = avoidRegularPatterns;
    }

    public void validateRanges() {
        requireOrdered(minOddCount, maxOddCount, "奇数数量范围");
        requireOrdered(minSmallCount, maxSmallCount, "小号数量范围");
        requireOrdered(minSum, maxSum, "和值范围");
        requireOrdered(minZoneCount, maxZoneCount, "三区数量范围");
        requireOrdered(minPrimeCount, maxPrimeCount, "质数数量范围");
        requireOrdered(minAcValue, maxAcValue, "AC 值范围");
    }

    private void requireOrdered(int min, int max, String name) {
        if (min > max) {
            throw new IllegalArgumentException(name + "的最小值不能大于最大值");
        }
    }

}
