package cn.lotterydcb.prediction;

import java.util.ArrayList;
import java.util.List;

/**
 * 在已有矩阵上做有限次单注替换，二码和三码覆盖均不退步时才接受替换
 */
final class RedCoverageOptimizer {

    private static final int MAX_PASSES = 2;

    private RedCoverageOptimizer() {
    }

    static List<Integer> optimize(List<List<Integer>> candidates, double[] scores, List<Integer> initial) {
        List<Features> features = candidates.stream().map(Features::from).toList();
        List<Integer> selected = new ArrayList<>(initial);
        boolean[] inUse = new boolean[candidates.size()];
        int[] pairs = new int[33 * 33];
        int[] triples = new int[33 * 33 * 33];
        int[] numbers = new int[33];
        for (int index : selected) {
            inUse[index] = true;
            update(features.get(index), pairs, triples, numbers, 1);
        }

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;
            for (int slot = 0; slot < selected.size(); slot++) {
                int original = selected.get(slot);
                update(features.get(original), pairs, triples, numbers, -1);
                Gain originalGain = gain(features.get(original), pairs, triples, numbers, scores[original]);
                int best = original;
                Gain bestGain = originalGain;
                for (int index = 0; index < features.size(); index++) {
                    if (inUse[index]) {
                        continue;
                    }
                    Gain next = gain(features.get(index), pairs, triples, numbers, scores[index]);
                    if (next.pairs() >= originalGain.pairs()
                            && next.triples() >= originalGain.triples()
                            && next.betterThan(bestGain)) {
                        best = index;
                        bestGain = next;
                    }
                }
                selected.set(slot, best);
                inUse[original] = false;
                inUse[best] = true;
                update(features.get(best), pairs, triples, numbers, 1);
                changed |= best != original;
            }
            if (!changed) {
                break;
            }
        }
        return List.copyOf(selected);
    }

    private static Gain gain(Features features, int[] pairs, int[] triples, int[] numbers, double score) {
        int newPairs = 0;
        int newTriples = 0;
        int usage = 0;
        for (int pair : features.pairs()) {
            if (pairs[pair] == 0) {
                newPairs++;
            }
        }
        for (int triple : features.triples()) {
            if (triples[triple] == 0) {
                newTriples++;
            }
        }
        for (int number : features.numbers()) {
            usage += numbers[number];
        }
        return new Gain(newPairs, newTriples, usage, score);
    }

    private static void update(Features features, int[] pairs, int[] triples, int[] numbers, int delta) {
        for (int pair : features.pairs()) {
            pairs[pair] += delta;
        }
        for (int triple : features.triples()) {
            triples[triple] += delta;
        }
        for (int number : features.numbers()) {
            numbers[number] += delta;
        }
    }

    private record Gain(int pairs, int triples, int usage, double score) {

        boolean betterThan(Gain other) {
            if (pairs != other.pairs) {
                return pairs > other.pairs;
            }
            if (triples != other.triples) {
                return triples > other.triples;
            }
            if (usage != other.usage) {
                return usage < other.usage;
            }
            return score > other.score;
        }

    }

    private record Features(int[] numbers, int[] pairs, int[] triples) {

        static Features from(List<Integer> redBalls) {
            int[] numbers = redBalls.stream().mapToInt(number -> number - 1).sorted().toArray();
            int[] pairs = new int[15];
            int[] triples = new int[20];
            int pairIndex = 0;
            int tripleIndex = 0;
            for (int left = 0; left < numbers.length; left++) {
                for (int middle = left + 1; middle < numbers.length; middle++) {
                    pairs[pairIndex++] = numbers[left] * 33 + numbers[middle];
                    for (int right = middle + 1; right < numbers.length; right++) {
                        triples[tripleIndex++] = (numbers[left] * 33 + numbers[middle]) * 33 + numbers[right];
                    }
                }
            }
            return new Features(numbers, pairs, triples);
        }

    }

}
