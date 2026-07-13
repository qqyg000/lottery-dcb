package cn.lotterydcb.prediction;

import java.util.List;

public record CompoundPredictionGroup(
        int sequence,
        List<Integer> redBalls,
        List<Integer> blueBalls,
        double score,
        int expandedTicketCount,
        int stakeAmountYuan,
        List<PredictionTicket> expandedTickets,
        List<String> highlights
) {

}
