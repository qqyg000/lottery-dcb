package cn.lotterydcb.prediction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberFeatureCalculatorTest {

    @Test
    void shouldCalculateKnownMetrics() {
        TicketMetrics metrics = NumberFeatureCalculator.calculate(List.of(3, 8, 12, 18, 25, 32));

        assertEquals(98, metrics.sum());
        assertEquals(2, metrics.oddCount());
        assertEquals(3, metrics.smallCount());
        assertEquals(List.of(2, 2, 2), metrics.zoneCounts());
        assertEquals(1, metrics.primeCount());
        assertEquals(5, metrics.compositeCount());
        assertEquals(List.of(5, 4, 6, 7, 7), metrics.gaps());
        assertEquals(29, metrics.span());
        assertEquals(9, metrics.acValue());
        assertEquals(4, metrics.distinctTailCount());
        assertTrue(metrics.regularPatternFlags().isEmpty());
    }

    @Test
    void shouldRejectArithmeticSequenceByDefault() {
        TicketMetrics metrics = NumberFeatureCalculator.calculate(List.of(1, 5, 9, 13, 17, 21));
        ConstraintEvaluation evaluation = ConstraintEvaluator.evaluate(metrics, new StrategyParameters());

        assertEquals(0, metrics.acValue());
        assertTrue(metrics.regularPatternFlags().contains("FOUR_TERM_ARITHMETIC"));
        assertFalse(evaluation.accepted());
    }

    @Test
    void acValueShouldAlwaysStayInTheoreticalRange() {
        java.util.SplittableRandom random = new java.util.SplittableRandom(20260713L);
        for (int sample = 0; sample < 10000; sample++) {
            List<Integer> numbers = random.ints(1, 34)
                    .distinct()
                    .limit(6)
                    .boxed()
                    .sorted()
                    .toList();
            int acValue = NumberFeatureCalculator.calculate(numbers).acValue();
            assertTrue(acValue >= 0 && acValue <= 10);
        }
    }

}
