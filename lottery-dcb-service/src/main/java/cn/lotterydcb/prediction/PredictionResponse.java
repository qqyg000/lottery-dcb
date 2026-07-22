package cn.lotterydcb.prediction;

import java.util.List;
import java.util.Map;

public record PredictionResponse(
        long seed,
        String generatedAt,
        int historyRecordCount,
        int lookback,
        BetMode betMode,
        Integer compoundRedCount,
        Integer compoundBlueCount,
        RotationMode rotationMode,
        BlueSelectionMode blueSelectionMode,
        int acceptedCandidateCount,
        int attempts,
        CoverageInfo coverage,
        List<PredictionTicket> tickets,
        List<CompoundPredictionGroup> compoundGroups,
        int expandedTicketCount,
        int totalStakeAmountYuan,
        Map<String, Integer> rejectionStatistics,
        String notice
) {

}
