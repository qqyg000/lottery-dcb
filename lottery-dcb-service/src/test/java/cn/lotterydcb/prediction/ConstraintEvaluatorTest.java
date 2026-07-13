package cn.lotterydcb.prediction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintEvaluatorTest {

    @Test
    void shouldAcceptBalancedGoldenSample() {
        TicketMetrics metrics = NumberFeatureCalculator.calculate(List.of(3, 8, 12, 18, 25, 32));

        assertTrue(ConstraintEvaluator.evaluate(metrics, new StrategyParameters()).accepted());
    }

    @Test
    void shouldRejectThreeConsecutiveNumbers() {
        TicketMetrics metrics = NumberFeatureCalculator.calculate(List.of(1, 2, 3, 10, 20, 30));
        ConstraintEvaluation evaluation = ConstraintEvaluator.evaluate(metrics, new StrategyParameters());

        assertFalse(evaluation.accepted());
        assertTrue(evaluation.rejectionReasons().contains("连号对数超限"));
        assertTrue(evaluation.rejectionReasons().contains("连续段长度超限"));
    }

    @Test
    void shouldRejectConcentratedTails() {
        TicketMetrics metrics = NumberFeatureCalculator.calculate(List.of(1, 11, 21, 24, 28, 32));
        ConstraintEvaluation evaluation = ConstraintEvaluator.evaluate(metrics, new StrategyParameters());

        assertFalse(evaluation.accepted());
        assertTrue(evaluation.rejectionReasons().contains("同尾号码过多"));
    }

}
