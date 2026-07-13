package cn.lotterydcb.prediction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NumberFeatureCalculator {

    private static final Set<Integer> PRIMES = Set.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31);

    private NumberFeatureCalculator() {
    }

    public static TicketMetrics calculate(List<Integer> redBalls) {
        if (redBalls == null || redBalls.size() != 6 || redBalls.stream().distinct().count() != 6) {
            throw new IllegalArgumentException("红球必须是 6 个不重复号码");
        }
        int[] numbers = redBalls.stream().mapToInt(Integer::intValue).sorted().toArray();
        if (numbers[0] < 1 || numbers[5] > 33) {
            throw new IllegalArgumentException("红球范围必须为 1 到 33");
        }

        int sum = Arrays.stream(numbers).sum();
        int oddCount = (int) Arrays.stream(numbers).filter(number -> number % 2 != 0).count();
        int smallCount = (int) Arrays.stream(numbers).filter(number -> number <= 16).count();
        int[] zones = new int[3];
        int primeCount = 0;
        int compositeCount = 0;
        int neutralCount = 0;
        Map<Integer, Integer> tailCounts = new HashMap<>();

        for (int number : numbers) {
            zones[Math.min((number - 1) / 11, 2)]++;
            if (PRIMES.contains(number)) {
                primeCount++;
            } else if (number == 1) {
                neutralCount++;
            } else {
                compositeCount++;
            }
            tailCounts.merge(number % 10, 1, Integer::sum);
        }

        List<Integer> gaps = new ArrayList<>();
        int consecutivePairs = 0;
        int maxRun = 1;
        int currentRun = 1;
        int maxGap = 0;
        for (int index = 1; index < numbers.length; index++) {
            int gap = numbers[index] - numbers[index - 1];
            gaps.add(gap);
            maxGap = Math.max(maxGap, gap);
            if (gap == 1) {
                consecutivePairs++;
                currentRun++;
                maxRun = Math.max(maxRun, currentRun);
            } else {
                currentRun = 1;
            }
        }

        Set<Integer> differences = new HashSet<>();
        for (int left = 0; left < numbers.length; left++) {
            for (int right = left + 1; right < numbers.length; right++) {
                differences.add(numbers[right] - numbers[left]);
            }
        }

        List<String> patternFlags = detectPatterns(numbers, gaps);
        return new TicketMetrics(
                sum,
                oddCount,
                6 - oddCount,
                smallCount,
                6 - smallCount,
                List.of(zones[0], zones[1], zones[2]),
                primeCount,
                compositeCount,
                neutralCount,
                List.copyOf(gaps),
                numbers[5] - numbers[0],
                maxGap,
                consecutivePairs,
                maxRun,
                differences.size() - 5,
                tailCounts.size(),
                tailCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0),
                patternFlags
        );
    }

    private static List<String> detectPatterns(int[] numbers, List<Integer> gaps) {
        List<String> flags = new ArrayList<>();
        Set<Integer> numberSet = Arrays.stream(numbers).boxed().collect(java.util.stream.Collectors.toSet());
        for (int start : numbers) {
            for (int step = 1; step <= 10; step++) {
                if (numberSet.contains(start + step)
                        && numberSet.contains(start + 2 * step)
                        && numberSet.contains(start + 3 * step)) {
                    flags.add("FOUR_TERM_ARITHMETIC");
                    start = numbers[numbers.length - 1];
                    break;
                }
            }
            if (flags.contains("FOUR_TERM_ARITHMETIC")) {
                break;
            }
        }

        if (gaps.size() == 5
                && gaps.get(0).equals(gaps.get(2))
                && gaps.get(2).equals(gaps.get(4))
                && gaps.get(1).equals(gaps.get(3))) {
            flags.add("ALTERNATING_GAPS");
        }

        Map<Integer, Integer> pairSums = new HashMap<>();
        for (int left = 0; left < numbers.length; left++) {
            for (int right = left + 1; right < numbers.length; right++) {
                pairSums.merge(numbers[left] + numbers[right], 1, Integer::sum);
            }
        }
        if (pairSums.values().stream().mapToInt(Integer::intValue).max().orElse(0) >= 3) {
            flags.add("THREE_SYMMETRIC_PAIRS");
        }
        return List.copyOf(flags);
    }

}
