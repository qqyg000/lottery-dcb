package cn.lotterydcb.statistics;

import java.util.List;

public record StatisticsResponse(
        int historyRecordCount,
        int lookback,
        List<NumberFrequency> redFrequencies,
        List<NumberFrequency> blueFrequencies,
        List<Integer> activeRedNumbers,
        List<Integer> quietRedNumbers,
        String notice
) {

}
