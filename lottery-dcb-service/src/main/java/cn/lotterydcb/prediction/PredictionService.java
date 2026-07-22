package cn.lotterydcb.prediction;

import cn.lotterydcb.history.DrawRecord;
import cn.lotterydcb.history.HistoryRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

@Service
public class PredictionService {

    private static final String NOTICE = "仅按约束筛选随机组合，不提高任意单注的理论中奖概率，请理性购彩";

    private static final int MAX_COMPOUND_GROUPS = 10;

    private static final int MAX_COMPOUND_EXPANDED_TICKETS = 10000;

    private static final int MAX_COMPOUND_SUBSET_EVALUATIONS = 300000;

    private final HistoryRepository historyRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public PredictionService(HistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public PredictionResponse generate(PredictionRequest request) {
        validateRequest(request);
        List<DrawRecord> history = historyRepository.snapshot().getRecords();
        HistoryProfile profile = buildProfile(history, request.getLookback());
        long seed = request.getSeed() == null
                ? secureRandom.nextLong() & 0x001FFFFFFFFFFFFFL
                : request.getSeed();
        SplittableRandom redRandom = new SplittableRandom(mix(seed ^ 0x6A09E667F3BCC909L));
        SplittableRandom blueRandom = new SplittableRandom(mix(seed ^ 0xBB67AE8584CAA73BL));

        if (request.getBetMode().isCompound()) {
            return generateCompoundResponse(
                    request,
                    history,
                    profile,
                    seed,
                    redRandom,
                    blueRandom
            );
        }

        GenerationBatch batch = request.getRotationMode() == RotationMode.NONE
                ? generateGlobalCandidates(request, profile, redRandom)
                : generateMatrixCandidates(request, profile, redRandom);

        List<Candidate> selected = switch (request.getRotationMode()) {
            case NONE -> selectDiverse(batch.candidates(), request.getTicketCount());
            case BALANCED_COVERAGE -> selectBalanced(batch.candidates(), request.getTicketCount());
            case PAIR_COVERAGE -> selectPairCoverage(batch.candidates(), request.getTicketCount());
        };

        if (selected.size() < request.getTicketCount()) {
            throw new GenerationException(
                    "当前约束只能生成 " + selected.size() + " 注，请扩大红球池、减少注数或放宽约束"
            );
        }

        List<Integer> blueBalls = selectBlueBalls(
                request.getBlueSelectionMode(),
                selected.size(),
                profile,
                blueRandom
        );
        List<PredictionTicket> tickets = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            Candidate candidate = selected.get(index);
            tickets.add(new PredictionTicket(
                    index + 1,
                    candidate.redBalls(),
                    blueBalls.get(index),
                    round(candidate.score(), 2),
                    candidate.metrics(),
                    highlights(candidate.metrics())
            ));
        }

        CoverageInfo coverage = calculateCoverage(selected, batch.redPool(), request.getRotationMode());
        return new PredictionResponse(
                seed,
                Instant.now().toString(),
                history.size(),
                profile.recordCount(),
                request.getBetMode(),
                null,
                null,
                request.getRotationMode(),
                request.getBlueSelectionMode(),
                batch.candidates().size(),
                batch.attempts(),
                coverage,
                List.copyOf(tickets),
                List.of(),
                tickets.size(),
                tickets.size() * 2,
                Map.copyOf(batch.rejectionStatistics()),
                NOTICE
        );
    }

    public PredictionRequest defaults() {
        return new PredictionRequest();
    }

    private PredictionResponse generateCompoundResponse(
            PredictionRequest request,
            List<DrawRecord> history,
            HistoryProfile profile,
            long seed,
            SplittableRandom redRandom,
            SplittableRandom blueRandom
    ) {
        CompoundGenerationBatch batch = generateCompoundCandidates(request, profile, redRandom);
        List<CompoundCandidate> selected = selectCompoundDiverse(
                batch.candidates(),
                request.getTicketCount()
        );
        List<List<Integer>> blueGroups = selectBlueGroups(
                request.getBlueSelectionMode(),
                selected.size(),
                request.getCompoundBlueCount(),
                profile,
                blueRandom
        );
        int redCombinationCount = Math.toIntExact(combinations(request.getCompoundRedCount(), 6));
        int expandedTicketsPerGroup = redCombinationCount * request.getCompoundBlueCount();

        List<CompoundPredictionGroup> groups = new ArrayList<>();
        List<Candidate> allExpandedCandidates = new ArrayList<>();
        List<PredictionTicket> allExpandedTickets = new ArrayList<>();
        int ticketSequence = 1;
        for (int groupIndex = 0; groupIndex < selected.size(); groupIndex++) {
            CompoundCandidate groupCandidate = selected.get(groupIndex);
            List<Integer> blueBalls = blueGroups.get(groupIndex);
            List<PredictionTicket> expandedTickets = new ArrayList<>();
            for (Candidate candidate : groupCandidate.expansions()) {
                allExpandedCandidates.add(candidate);
                for (int blueBall : blueBalls) {
                    expandedTickets.add(new PredictionTicket(
                            ticketSequence++,
                            candidate.redBalls(),
                            blueBall,
                            round(candidate.score(), 2),
                            candidate.metrics(),
                            highlights(candidate.metrics())
                    ));
                }
            }
            allExpandedTickets.addAll(expandedTickets);
            groups.add(new CompoundPredictionGroup(
                    groupIndex + 1,
                    groupCandidate.redBalls(),
                    blueBalls,
                    round(groupCandidate.score(), 2),
                    expandedTicketsPerGroup,
                    expandedTicketsPerGroup * 2,
                    List.copyOf(expandedTickets),
                    List.of(
                            request.getCompoundRedCount() + " 个红球任选 6 个",
                            request.getCompoundBlueCount() + " 个蓝球任选 1 个",
                            "完整展开 " + expandedTicketsPerGroup + " 注",
                            "每个六红组合均通过策略"
                    )
            ));
        }

        List<Integer> compoundPool = selected.stream()
                .flatMap(candidate -> candidate.redBalls().stream())
                .distinct()
                .sorted()
                .toList();
        CoverageInfo baseCoverage = calculateCoverage(
                allExpandedCandidates,
                compoundPool,
                RotationMode.NONE
        );
        CoverageInfo coverage = new CoverageInfo(
                baseCoverage.redPool(),
                baseCoverage.uniqueRedCount(),
                baseCoverage.coveredPairCount(),
                baseCoverage.possiblePairCount(),
                baseCoverage.pairCoverageRatio(),
                request.getCompoundRedCount() + "+" + request.getCompoundBlueCount()
                        + " 复式完整展开后的实际红球二码覆盖率"
        );
        int expandedTicketCount = groups.size() * expandedTicketsPerGroup;
        return new PredictionResponse(
                seed,
                Instant.now().toString(),
                history.size(),
                profile.recordCount(),
                request.getBetMode(),
                request.getCompoundRedCount(),
                request.getCompoundBlueCount(),
                RotationMode.NONE,
                request.getBlueSelectionMode(),
                batch.candidates().size(),
                batch.attempts(),
                coverage,
                List.copyOf(allExpandedTickets),
                List.copyOf(groups),
                expandedTicketCount,
                expandedTicketCount * 2,
                Map.copyOf(batch.rejectionStatistics()),
                NOTICE
        );
    }

    private CompoundGenerationBatch generateCompoundCandidates(
            PredictionRequest request,
            HistoryProfile profile,
            SplittableRandom random
    ) {
        int target = Math.min(
                request.getCandidatePoolSize(),
                Math.max(60, request.getTicketCount() * 30)
        );
        long redCombinationCount = combinations(request.getCompoundRedCount(), 6);
        int maxAttempts = Math.min(300000, Math.max(10000, target * 150));
        int evaluationLimitedAttempts = (int) Math.max(
                1,
                MAX_COMPOUND_SUBSET_EVALUATIONS / redCombinationCount
        );
        maxAttempts = Math.min(maxAttempts, evaluationLimitedAttempts);
        Map<Long, CompoundCandidate> candidates = new LinkedHashMap<>();
        Map<String, Integer> rejectionStatistics = new LinkedHashMap<>();
        int attempts = 0;

        while (candidates.size() < target && attempts < maxAttempts) {
            attempts++;
            List<Integer> redBalls = sampleNumbers(
                    request.getCompoundRedCount(),
                    33,
                    profile.redWeights(),
                    random
            );
            List<List<Integer>> combinations = new ArrayList<>();
            enumerateCombinations(redBalls, 0, new ArrayList<>(), combinations);
            List<Candidate> expansions = new ArrayList<>();
            boolean accepted = true;
            for (List<Integer> combination : combinations) {
                Candidate candidate = evaluate(combination, request.getStrategy(), profile);
                if (candidate == null) {
                    countRejections(combination, request.getStrategy(), rejectionStatistics);
                    accepted = false;
                    break;
                }
                expansions.add(candidate);
            }
            if (accepted) {
                double averageScore = expansions.stream().mapToDouble(Candidate::score).average().orElse(0.0);
                double minimumScore = expansions.stream().mapToDouble(Candidate::score).min().orElse(0.0);
                double score = minimumScore * 0.7 + averageScore * 0.3;
                long candidateMask = mask(redBalls);
                candidates.putIfAbsent(candidateMask, new CompoundCandidate(
                        List.copyOf(redBalls),
                        List.copyOf(expansions),
                        score,
                        candidateMask
                ));
            }
        }

        if (candidates.size() < request.getTicketCount()) {
            throw impossibleConstraints(candidates.size(), attempts, rejectionStatistics);
        }
        return new CompoundGenerationBatch(
                List.copyOf(candidates.values()),
                attempts,
                rejectionStatistics
        );
    }

    private GenerationBatch generateGlobalCandidates(
            PredictionRequest request,
            HistoryProfile profile,
            SplittableRandom random
    ) {
        int target = Math.min(request.getCandidatePoolSize(), 20000);
        int maxAttempts = Math.min(600000, Math.max(10000, target * 25));
        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        Map<String, Integer> rejectionStatistics = new LinkedHashMap<>();
        int attempts = 0;

        while (candidates.size() < target && attempts < maxAttempts) {
            attempts++;
            List<Integer> redBalls = sampleNumbers(6, 33, profile.redWeights(), random);
            Candidate candidate = evaluate(redBalls, request.getStrategy(), profile);
            if (candidate != null) {
                candidates.putIfAbsent(candidate.mask(), candidate);
            } else {
                countRejections(redBalls, request.getStrategy(), rejectionStatistics);
            }
        }

        if (candidates.size() < request.getTicketCount()) {
            throw impossibleConstraints(candidates.size(), attempts, rejectionStatistics);
        }
        return new GenerationBatch(
                List.copyOf(candidates.values()),
                attempts,
                rejectionStatistics,
                List.of()
        );
    }

    private GenerationBatch generateMatrixCandidates(
            PredictionRequest request,
            HistoryProfile profile,
            SplittableRandom random
    ) {
        long combinationCount = combinations(request.getRedPoolSize(), 6);
        if (combinationCount < request.getTicketCount()) {
            throw new GenerationException(
                    request.getRedPoolSize() + " 个红球最多只有 " + combinationCount
                            + " 个六数组合，无法生成 " + request.getTicketCount() + " 注"
            );
        }

        int trials = Math.min(350, Math.max(40, request.getCandidatePoolSize() / 50));
        List<Candidate> bestCandidates = List.of();
        List<Integer> bestPool = List.of();
        Map<String, Integer> rejectionStatistics = new LinkedHashMap<>();
        int attempts = 0;

        for (int trial = 0; trial < trials; trial++) {
            List<Integer> pool = sampleNumbers(
                    request.getRedPoolSize(),
                    33,
                    profile.redWeights(),
                    random
            );
            List<List<Integer>> combinations = new ArrayList<>();
            enumerateCombinations(pool, 0, new ArrayList<>(), combinations);
            List<Candidate> valid = new ArrayList<>();
            for (List<Integer> redBalls : combinations) {
                attempts++;
                Candidate candidate = evaluate(redBalls, request.getStrategy(), profile);
                if (candidate != null) {
                    valid.add(candidate);
                } else {
                    countRejections(redBalls, request.getStrategy(), rejectionStatistics);
                }
            }
            valid.sort(Comparator.comparingDouble(Candidate::score).reversed());
            if (matrixQuality(valid, pool) > matrixQuality(bestCandidates, bestPool)) {
                bestCandidates = List.copyOf(valid);
                bestPool = List.copyOf(pool);
            }
            if (valid.size() >= Math.max(request.getTicketCount() * 3, request.getTicketCount() + 12)
                    && usesEveryPoolNumber(valid, pool)) {
                break;
            }
        }

        if (bestCandidates.size() < request.getTicketCount()) {
            throw impossibleConstraints(bestCandidates.size(), attempts, rejectionStatistics);
        }
        return new GenerationBatch(bestCandidates, attempts, rejectionStatistics, bestPool);
    }

    private Candidate evaluate(
            List<Integer> redBalls,
            StrategyParameters strategy,
            HistoryProfile profile
    ) {
        TicketMetrics metrics = NumberFeatureCalculator.calculate(redBalls);
        ConstraintEvaluation evaluation = ConstraintEvaluator.evaluate(metrics, strategy);
        if (!evaluation.accepted()) {
            return null;
        }
        double historyBalance = redBalls.stream()
                .mapToDouble(number -> profile.balanceScores()[number])
                .average()
                .orElse(0.0);
        double score = ConstraintEvaluator.score(metrics) * 0.95 + historyBalance * 5.0;
        return new Candidate(List.copyOf(redBalls), metrics, score, mask(redBalls));
    }

    private List<Candidate> selectDiverse(List<Candidate> candidates, int count) {
        List<Candidate> remaining = new ArrayList<>(candidates);
        List<Candidate> selected = new ArrayList<>();
        Map<Integer, Integer> usages = new HashMap<>();
        while (selected.size() < count && !remaining.isEmpty()) {
            Candidate best = remaining.stream()
                    .max(Comparator.comparingDouble(candidate ->
                            candidate.score()
                                    - maxIntersection(candidate, selected) * 4.5
                                    - usageCost(candidate, usages) * 0.55
                    ))
                    .orElseThrow();
            selected.add(best);
            best.redBalls().forEach(number -> usages.merge(number, 1, Integer::sum));
            remaining.remove(best);
        }
        return List.copyOf(selected);
    }

    private List<Candidate> selectBalanced(List<Candidate> candidates, int count) {
        List<Candidate> remaining = new ArrayList<>(candidates);
        List<Candidate> selected = new ArrayList<>();
        Map<Integer, Integer> usages = new HashMap<>();
        while (selected.size() < count && !remaining.isEmpty()) {
            Candidate best = remaining.stream()
                    .max(Comparator.comparingDouble(candidate ->
                            candidate.score()
                                    - usageCost(candidate, usages) * 5.0
                                    - maxIntersection(candidate, selected) * 2.0
                    ))
                    .orElseThrow();
            selected.add(best);
            best.redBalls().forEach(number -> usages.merge(number, 1, Integer::sum));
            remaining.remove(best);
        }
        return List.copyOf(selected);
    }

    private List<Candidate> selectPairCoverage(List<Candidate> candidates, int count) {
        List<Candidate> remaining = new ArrayList<>(candidates);
        List<Candidate> selected = new ArrayList<>();
        Set<Long> coveredPairs = new HashSet<>();
        while (selected.size() < count && !remaining.isEmpty()) {
            Candidate best = remaining.stream()
                    .max(Comparator.comparingDouble(candidate ->
                            newPairCount(candidate, coveredPairs) * 12.0
                                    + candidate.score()
                                    - maxIntersection(candidate, selected) * 1.5
                    ))
                    .orElseThrow();
            selected.add(best);
            coveredPairs.addAll(pairs(best.redBalls()));
            remaining.remove(best);
        }
        return List.copyOf(selected);
    }

    private List<CompoundCandidate> selectCompoundDiverse(
            List<CompoundCandidate> candidates,
            int count
    ) {
        List<CompoundCandidate> remaining = new ArrayList<>(candidates);
        List<CompoundCandidate> selected = new ArrayList<>();
        Map<Integer, Integer> usages = new HashMap<>();
        while (selected.size() < count && !remaining.isEmpty()) {
            CompoundCandidate best = remaining.stream()
                    .max(Comparator.comparingDouble(candidate ->
                            candidate.score()
                                    - maxCompoundIntersection(candidate, selected) * 4.5
                                    - compoundUsageCost(candidate, usages) * 0.55
                    ))
                    .orElseThrow();
            selected.add(best);
            best.redBalls().forEach(number -> usages.merge(number, 1, Integer::sum));
            remaining.remove(best);
        }
        return List.copyOf(selected);
    }

    private List<Integer> selectBlueBalls(
            BlueSelectionMode mode,
            int count,
            HistoryProfile profile,
            SplittableRandom random
    ) {
        List<Integer> result = new ArrayList<>();
        while (result.size() < count) {
            List<Integer> cycle = switch (mode) {
                case RANDOM -> shuffledRange(16, random);
                case FREQUENCY_BALANCED -> weightedBluePermutation(profile.blueFrequency(), random);
                case COLD_HOT_MIX -> coldHotBluePermutation(profile.blueFrequency(), random);
            };
            for (int number : cycle) {
                if (result.size() >= count) {
                    break;
                }
                result.add(number);
            }
        }
        return List.copyOf(result);
    }

    private List<List<Integer>> selectBlueGroups(
            BlueSelectionMode mode,
            int groupCount,
            int blueCount,
            HistoryProfile profile,
            SplittableRandom random
    ) {
        List<List<Integer>> groups = new ArrayList<>();
        for (int index = 0; index < groupCount; index++) {
            List<Integer> permutation = switch (mode) {
                case RANDOM -> shuffledRange(16, random);
                case FREQUENCY_BALANCED -> weightedBluePermutation(profile.blueFrequency(), random);
                case COLD_HOT_MIX -> coldHotBluePermutation(profile.blueFrequency(), random);
            };
            groups.add(permutation.stream().limit(blueCount).sorted().toList());
        }
        return List.copyOf(groups);
    }

    private List<Integer> weightedBluePermutation(int[] frequencies, SplittableRandom random) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int number = 1; number <= 16; number++) {
            min = Math.min(min, frequencies[number]);
            max = Math.max(max, frequencies[number]);
        }
        double[] weights = new double[17];
        double range = Math.max(1.0, max - min);
        for (int number = 1; number <= 16; number++) {
            weights[number] = 0.8 + 0.4 * (max - frequencies[number]) / range;
        }
        return weightedOrder(16, weights, random);
    }

    private List<Integer> coldHotBluePermutation(int[] frequencies, SplittableRandom random) {
        List<Integer> sorted = new ArrayList<>();
        for (int number = 1; number <= 16; number++) {
            sorted.add(number);
        }
        sorted.sort(Comparator.comparingInt(number -> frequencies[number]));
        List<Integer> mixed = new ArrayList<>();
        int left = 0;
        int right = sorted.size() - 1;
        boolean coldFirst = random.nextBoolean();
        while (left <= right) {
            if (coldFirst) {
                mixed.add(sorted.get(left++));
                if (left <= right) {
                    mixed.add(sorted.get(right--));
                }
            } else {
                mixed.add(sorted.get(right--));
                if (left <= right) {
                    mixed.add(sorted.get(left++));
                }
            }
        }
        return List.copyOf(mixed);
    }

    private HistoryProfile buildProfile(List<DrawRecord> history, int lookback) {
        List<DrawRecord> window = history.stream().limit(Math.min(lookback, history.size())).toList();
        int[] redFrequency = new int[34];
        int[] blueFrequency = new int[17];
        for (DrawRecord record : window) {
            record.redBalls().forEach(number -> redFrequency[number]++);
            blueFrequency[record.blueBall()]++;
        }

        double[] redWeights = new double[34];
        double[] balanceScores = new double[34];
        double expected = window.isEmpty() ? 0.0 : window.size() * 6.0 / 33.0;
        double maxDeviation = 1.0;
        for (int number = 1; number <= 33; number++) {
            maxDeviation = Math.max(maxDeviation, Math.abs(redFrequency[number] - expected));
        }
        for (int number = 1; number <= 33; number++) {
            double normalizedDeviation = Math.abs(redFrequency[number] - expected) / maxDeviation;
            balanceScores[number] = 1.0 - normalizedDeviation;
            redWeights[number] = 0.9 + balanceScores[number] * 0.2;
        }
        return new HistoryProfile(window.size(), redFrequency, blueFrequency, redWeights, balanceScores);
    }

    private CoverageInfo calculateCoverage(
            List<Candidate> selected,
            List<Integer> matrixPool,
            RotationMode mode
    ) {
        Set<Integer> union = new LinkedHashSet<>();
        Set<Long> coveredPairs = new HashSet<>();
        selected.forEach(candidate -> {
            union.addAll(candidate.redBalls());
            coveredPairs.addAll(pairs(candidate.redBalls()));
        });
        List<Integer> pool = matrixPool.isEmpty()
                ? union.stream().sorted().toList()
                : matrixPool.stream().sorted().toList();
        int possiblePairs = pool.size() < 2 ? 0 : pool.size() * (pool.size() - 1) / 2;
        double ratio = possiblePairs == 0 ? 0.0 : (double) coveredPairs.size() / possiblePairs;
        String description = switch (mode) {
            case NONE -> "独立优选组合的实际二码覆盖率";
            case BALANCED_COVERAGE -> "矩阵红球池内号码使用均衡后的实际二码覆盖率";
            case PAIR_COVERAGE -> "矩阵红球池内贪心二码覆盖率，不代表中奖保证";
        };
        return new CoverageInfo(
                pool,
                union.size(),
                coveredPairs.size(),
                possiblePairs,
                round(ratio, 4),
                description
        );
    }

    private List<Integer> sampleNumbers(
            int count,
            int maxNumber,
            double[] weights,
            SplittableRandom random
    ) {
        return weightedOrder(maxNumber, weights, random).stream()
                .limit(count)
                .sorted()
                .toList();
    }

    private List<Integer> weightedOrder(
            int maxNumber,
            double[] weights,
            SplittableRandom random
    ) {
        List<WeightedNumber> weighted = new ArrayList<>();
        for (int number = 1; number <= maxNumber; number++) {
            double weight = weights == null || number >= weights.length ? 1.0 : Math.max(0.01, weights[number]);
            double uniform = Math.max(1.0e-12, random.nextDouble());
            weighted.add(new WeightedNumber(number, -Math.log(uniform) / weight));
        }
        return weighted.stream()
                .sorted(Comparator.comparingDouble(WeightedNumber::key))
                .map(WeightedNumber::number)
                .toList();
    }

    private List<Integer> shuffledRange(int maxNumber, SplittableRandom random) {
        List<Integer> numbers = new ArrayList<>();
        for (int number = 1; number <= maxNumber; number++) {
            numbers.add(number);
        }
        for (int index = numbers.size() - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            int value = numbers.get(index);
            numbers.set(index, numbers.get(swapIndex));
            numbers.set(swapIndex, value);
        }
        return List.copyOf(numbers);
    }

    private void enumerateCombinations(
            List<Integer> pool,
            int start,
            List<Integer> current,
            List<List<Integer>> output
    ) {
        if (current.size() == 6) {
            output.add(List.copyOf(current));
            return;
        }
        int needed = 6 - current.size();
        for (int index = start; index <= pool.size() - needed; index++) {
            current.add(pool.get(index));
            enumerateCombinations(pool, index + 1, current, output);
            current.remove(current.size() - 1);
        }
    }

    private void countRejections(
            List<Integer> redBalls,
            StrategyParameters strategy,
            Map<String, Integer> statistics
    ) {
        TicketMetrics metrics = NumberFeatureCalculator.calculate(redBalls);
        ConstraintEvaluator.evaluate(metrics, strategy).rejectionReasons()
                .forEach(reason -> statistics.merge(reason, 1, Integer::sum));
    }

    private GenerationException impossibleConstraints(
            int candidateCount,
            int attempts,
            Map<String, Integer> rejectionStatistics
    ) {
        String mainReason = rejectionStatistics.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("约束组合冲突");
        return new GenerationException(
                "尝试 " + attempts + " 次仅得到 " + candidateCount + " 个候选，主要原因：" + mainReason
                        + "，请放宽对应范围"
        );
    }

    private int maxIntersection(Candidate candidate, List<Candidate> selected) {
        return selected.stream()
                .mapToInt(existing -> Long.bitCount(candidate.mask() & existing.mask()))
                .max()
                .orElse(0);
    }

    private int maxCompoundIntersection(
            CompoundCandidate candidate,
            List<CompoundCandidate> selected
    ) {
        return selected.stream()
                .mapToInt(existing -> Long.bitCount(candidate.mask() & existing.mask()))
                .max()
                .orElse(0);
    }

    private int compoundUsageCost(
            CompoundCandidate candidate,
            Map<Integer, Integer> usages
    ) {
        return candidate.redBalls().stream()
                .mapToInt(number -> usages.getOrDefault(number, 0))
                .sum();
    }

    private int usageCost(Candidate candidate, Map<Integer, Integer> usages) {
        return candidate.redBalls().stream().mapToInt(number -> usages.getOrDefault(number, 0)).sum();
    }

    private int newPairCount(Candidate candidate, Set<Long> coveredPairs) {
        return (int) pairs(candidate.redBalls()).stream().filter(pair -> !coveredPairs.contains(pair)).count();
    }

    private Set<Long> pairs(List<Integer> redBalls) {
        Set<Long> pairs = new HashSet<>();
        for (int left = 0; left < redBalls.size(); left++) {
            for (int right = left + 1; right < redBalls.size(); right++) {
                pairs.add(((long) redBalls.get(left) << 6) | redBalls.get(right));
            }
        }
        return pairs;
    }

    private long mask(List<Integer> redBalls) {
        long mask = 0L;
        for (int number : redBalls) {
            mask |= 1L << (number - 1);
        }
        return mask;
    }

    private boolean usesEveryPoolNumber(List<Candidate> candidates, List<Integer> pool) {
        Set<Integer> used = new HashSet<>();
        candidates.forEach(candidate -> used.addAll(candidate.redBalls()));
        return used.containsAll(pool);
    }

    private double matrixQuality(List<Candidate> candidates, List<Integer> pool) {
        if (candidates.isEmpty() || pool.isEmpty()) {
            return 0.0;
        }
        double averageScore = candidates.stream().mapToDouble(Candidate::score).average().orElse(0.0);
        return candidates.size() * 100.0 + averageScore + (usesEveryPoolNumber(candidates, pool) ? 50.0 : 0.0);
    }

    private List<String> highlights(TicketMetrics metrics) {
        return List.of(
                "奇偶 " + metrics.oddCount() + ":" + metrics.evenCount(),
                "大小 " + metrics.smallCount() + ":" + metrics.largeCount(),
                "三区 " + metrics.zoneCounts().get(0) + ":" + metrics.zoneCounts().get(1) + ":" + metrics.zoneCounts().get(2),
                "和值 " + metrics.sum(),
                "AC " + metrics.acValue(),
                "尾数 " + metrics.distinctTailCount() + " 种"
        );
    }

    private long combinations(int total, int choose) {
        if (choose < 0 || choose > total) {
            return 0;
        }
        long result = 1;
        for (int index = 1; index <= choose; index++) {
            result = result * (total - choose + index) / index;
        }
        return result;
    }

    private void validateRequest(PredictionRequest request) {
        if (request == null || request.getStrategy() == null || request.getBetMode() == null) {
            throw new IllegalArgumentException("生成参数不能为空");
        }
        if (request.getBetMode().isCompound()) {
            validateCompoundRequest(request);
        }
        request.getStrategy().validateRanges();
    }

    private void validateCompoundRequest(PredictionRequest request) {
        if (request.getTicketCount() > MAX_COMPOUND_GROUPS) {
            throw new IllegalArgumentException("复式每次最多生成 " + MAX_COMPOUND_GROUPS + " 组");
        }
        if (request.getCompoundRedCount() < 6 || request.getCompoundRedCount() > 33) {
            throw new IllegalArgumentException("复式红球数量必须为 6 到 33");
        }
        if (request.getCompoundBlueCount() < 1 || request.getCompoundBlueCount() > 16) {
            throw new IllegalArgumentException("复式蓝球数量必须为 1 到 16");
        }
        if (request.getCompoundRedCount() == 6 && request.getCompoundBlueCount() == 1) {
            throw new IllegalArgumentException("6+1 是单式，复式红球至少 7 个或蓝球至少 2 个");
        }

        long expandedTicketsPerGroup = combinations(request.getCompoundRedCount(), 6)
                * request.getCompoundBlueCount();
        long totalExpandedTickets = expandedTicketsPerGroup * request.getTicketCount();
        if (totalExpandedTickets > MAX_COMPOUND_EXPANDED_TICKETS) {
            throw new IllegalArgumentException(
                    "当前配置将展开 " + totalExpandedTickets + " 注，最多允许 "
                            + MAX_COMPOUND_EXPANDED_TICKETS + " 注，请减少红球、蓝球或复式组数"
            );
        }
    }

    private long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private record Candidate(List<Integer> redBalls, TicketMetrics metrics, double score, long mask) {

    }

    private record CompoundCandidate(
            List<Integer> redBalls,
            List<Candidate> expansions,
            double score,
            long mask
    ) {

    }

    private record CompoundGenerationBatch(
            List<CompoundCandidate> candidates,
            int attempts,
            Map<String, Integer> rejectionStatistics
    ) {

    }

    private record GenerationBatch(
            List<Candidate> candidates,
            int attempts,
            Map<String, Integer> rejectionStatistics,
            List<Integer> redPool
    ) {

    }

    private record HistoryProfile(
            int recordCount,
            int[] redFrequency,
            int[] blueFrequency,
            double[] redWeights,
            double[] balanceScores
    ) {

    }

    private record WeightedNumber(int number, double key) {

    }

}
