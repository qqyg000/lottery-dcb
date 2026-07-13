package cn.lotterydcb.statistics;

public record NumberFrequency(
        int number,
        int count,
        double occurrenceRate,
        int missingDraws
) {

}
