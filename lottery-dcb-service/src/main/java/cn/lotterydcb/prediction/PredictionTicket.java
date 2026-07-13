package cn.lotterydcb.prediction;

import java.util.List;

public record PredictionTicket(
        int sequence,
        List<Integer> redBalls,
        int blueBall,
        double score,
        TicketMetrics metrics,
        List<String> highlights
) {

}
