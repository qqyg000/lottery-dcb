package cn.lotterydcb.prediction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedCoverageOptimizerTest {

    @Test
    void shouldPreferAdditionalCoverageOverShapeScore() {
        List<List<Integer>> candidates = List.of(
                List.of(1, 2, 3, 4, 5, 6),
                List.of(1, 2, 3, 4, 5, 7),
                List.of(1, 2, 3, 7, 8, 9)
        );
        List<Integer> initial = List.of(0, 1);

        List<Integer> selected = RedCoverageOptimizer.optimize(candidates, new double[]{100, 99, 1}, initial);

        assertTrue(selected.contains(2));
        assertTrue(coverage(candidates, selected, 2) > coverage(candidates, initial, 2));
        assertTrue(coverage(candidates, selected, 3) > coverage(candidates, initial, 3));
    }

    @Test
    void replacementsShouldPreserveBothCoverageCountsAndTicketBudget() {
        for (long seed = 0; seed < 30; seed++) {
            SplittableRandom random = new SplittableRandom(seed);
            Set<List<Integer>> unique = new java.util.LinkedHashSet<>();
            while (unique.size() < 60) {
                unique.add(random.ints(1, 13).distinct().limit(6).sorted().boxed().toList());
            }
            List<List<Integer>> candidates = new ArrayList<>(unique);
            double[] scores = random.doubles(60, 50, 100).toArray();
            List<Integer> initial = List.of(0, 1, 2, 3, 4, 5, 6, 7);

            List<Integer> selected = RedCoverageOptimizer.optimize(candidates, scores, initial);

            assertEquals(initial.size(), selected.size());
            assertEquals(selected.size(), new HashSet<>(selected).size());
            assertTrue(coverage(candidates, selected, 2) >= coverage(candidates, initial, 2));
            assertTrue(coverage(candidates, selected, 3) >= coverage(candidates, initial, 3));
            assertEquals(selected, RedCoverageOptimizer.optimize(candidates, scores, initial));
        }
    }

    @Test
    void shouldRetainSelectionWhenEveryCandidateIsAlreadyUsed() {
        List<List<Integer>> candidates = List.of(List.of(1, 2, 3, 4, 5, 33));

        assertEquals(List.of(0), RedCoverageOptimizer.optimize(candidates, new double[]{80}, List.of(0)));
    }

    private int coverage(List<List<Integer>> candidates, List<Integer> selected, int size) {
        Set<Set<Integer>> covered = new HashSet<>();
        for (int index : selected) {
            List<Integer> numbers = candidates.get(index);
            for (int left = 0; left < numbers.size(); left++) {
                for (int middle = left + 1; middle < numbers.size(); middle++) {
                    if (size == 2) {
                        covered.add(Set.of(numbers.get(left), numbers.get(middle)));
                    } else {
                        for (int right = middle + 1; right < numbers.size(); right++) {
                            covered.add(Set.of(numbers.get(left), numbers.get(middle), numbers.get(right)));
                        }
                    }
                }
            }
        }
        return covered.size();
    }

}
