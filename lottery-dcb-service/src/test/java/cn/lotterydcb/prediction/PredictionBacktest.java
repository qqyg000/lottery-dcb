package cn.lotterydcb.prediction;

import cn.lotterydcb.config.LotteryHistoryProperties;
import cn.lotterydcb.history.DrawRecord;
import cn.lotterydcb.history.HistoryArchive;
import cn.lotterydcb.history.HistoryRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * 离线逐期回测入口，每期只读取该期以前的 100 期数据，不联网或改写历史文件
 */
public final class PredictionBacktest {

    private static final int LOOKBACK = 100;

    private PredictionBacktest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("参数：历史 JSON 路径、输出 JSON 路径、回测期数、每期种子数");
        }
        int requestedDraws = Integer.parseInt(args[2]);
        int seeds = Integer.parseInt(args[3]);
        if (requestedDraws < 1 || requestedDraws > 1000 || seeds < 1 || seeds > 20) {
            throw new IllegalArgumentException("回测期数必须为 1 到 1000，每期种子数必须为 1 到 20");
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        byte[] historyBytes = Files.readAllBytes(Path.of(args[0]));
        HistoryArchive archive = gson.fromJson(new String(historyBytes, StandardCharsets.UTF_8), HistoryArchive.class);
        List<DrawRecord> history = archive.getRecords().stream()
                .filter(DrawRecord::isValid)
                .sorted(Comparator.comparing(DrawRecord::issue).reversed())
                .toList();
        if (history.stream().map(DrawRecord::issue).distinct().count() != history.size()) {
            throw new IllegalArgumentException("历史期号重复，请先清理数据");
        }
        int drawCount = Math.min(requestedDraws, history.size() - LOOKBACK);
        if (drawCount < 1) {
            throw new IllegalArgumentException("回测至少需要 101 期有效历史数据，内置 seed 不足以用于回测");
        }
        WindowRepository repository = new WindowRepository();
        PredictionService service = new PredictionService(repository);
        List<Map<String, Object>> scenarios = new ArrayList<>();
        for (BetMode mode : List.of(BetMode.STANDARD, BetMode.COMPOUND)) {
            Totals actual = new Totals();
            Totals randomBaseline = new Totals();
            int generationFailures = 0;
            long started = System.nanoTime();
            for (int index = drawCount - 1; index >= 0; index--) {
                DrawRecord draw = history.get(index);
                repository.setWindow(history.subList(index + 1, index + 1 + LOOKBACK));
                for (int repetition = 0; repetition < seeds; repetition++) {
                    long seed = 20260913L + Long.parseLong(draw.issue()) * 31 + repetition * 1000003L;
                    PredictionRequest request = new PredictionRequest();
                    request.setBetMode(mode);
                    request.setTicketCount(mode == BetMode.COMPOUND ? 2 : 8);
                    request.setSeed(seed);
                    int budget = mode == BetMode.COMPOUND ? 28 : 8;
                    randomBaseline.add(randomTickets(budget, seed), draw);
                    try {
                        PredictionResponse response = service.generate(request);
                        if (response.expandedTicketCount() != budget) {
                            throw new IllegalStateException("回测注数与预设预算不一致");
                        }
                        actual.add(response.tickets(), draw);
                    } catch (GenerationException exception) {
                        generationFailures++;
                        // 失败期按零命中保留在分母中，防止只统计成功期导致结果偏高
                        actual.add(List.of(), draw);
                    }
                }
            }
            Map<String, Object> scenario = new LinkedHashMap<>();
            scenario.put("mode", mode);
            scenario.put("ticketsPerDraw", mode == BetMode.COMPOUND ? 28 : 8);
            scenario.put("generationFailures", generationFailures);
            scenario.put("algorithm", actual.summary());
            scenario.put("uniformRandomSameBudget", randomBaseline.summary());
            scenario.put("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000.0);
            scenarios.add(scenario);
            System.out.println(mode + " completed: " + drawCount * seeds + " cases");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("historyRecordCount", history.size());
        report.put("historySha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(historyBytes)));
        report.put("firstIssue", history.get(drawCount - 1).issue());
        report.put("lastIssue", history.get(0).issue());
        report.put("drawCount", drawCount);
        report.put("seedsPerDraw", seeds);
        report.put("lookback", LOOKBACK);
        report.put("candidatePoolSize", new PredictionRequest().getCandidatePoolSize());
        report.put("seedRule", "20260913 + issue * 31 + repetition * 1000003; repetition starts at 0");
        report.put("strategy", new PredictionRequest().getStrategy());
        report.put("note", "每期只使用更早历史；同一期多种子不是独立开奖样本；红球指标是至少一注达到阈值，蓝球指标是整批至少覆盖一次；均匀随机基线使用相同注数、无形态约束的去重单式，未匹配复式结构；历史结果不保证未来表现");
        report.put("scenarios", scenarios);
        Path output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, gson.toJson(report), StandardCharsets.UTF_8);
    }

    private static List<PredictionTicket> randomTickets(int count, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        Set<String> used = new HashSet<>();
        List<PredictionTicket> tickets = new ArrayList<>();
        while (tickets.size() < count) {
            List<Integer> reds = random.ints(1, 34).distinct().limit(6).sorted().boxed().toList();
            int blue = random.nextInt(1, 17);
            if (used.add(reds + ":" + blue)) {
                tickets.add(new PredictionTicket(tickets.size() + 1, reds, blue, 0, null, List.of()));
            }
        }
        return tickets;
    }

    private static final class WindowRepository extends HistoryRepository {

        private final HistoryArchive window = new HistoryArchive();

        private WindowRepository() {
            super(new LotteryHistoryProperties(), new Gson());
        }

        void setWindow(List<DrawRecord> records) {
            window.setRecords(List.copyOf(records));
        }

        @Override
        public HistoryArchive snapshot() {
            return window;
        }

    }

    private static final class Totals {

        private int cases;

        private int blueHits;

        private int redThreeHits;

        private int redFourHits;

        private long blueCoverage;

        private long pairCoverage;

        private long tripleCoverage;

        void add(List<PredictionTicket> tickets, DrawRecord draw) {
            cases++;
            Set<Integer> blues = new HashSet<>();
            Set<Long> pairs = new HashSet<>();
            Set<Long> triples = new HashSet<>();
            int bestRed = 0;
            for (PredictionTicket ticket : tickets) {
                blues.add(ticket.blueBall());
                List<Integer> reds = ticket.redBalls();
                bestRed = Math.max(bestRed, (int) reds.stream().filter(draw.redBalls()::contains).count());
                for (int left = 0; left < reds.size(); left++) {
                    for (int middle = left + 1; middle < reds.size(); middle++) {
                        pairs.add((1L << reds.get(left)) | (1L << reds.get(middle)));
                        for (int right = middle + 1; right < reds.size(); right++) {
                            triples.add((1L << reds.get(left)) | (1L << reds.get(middle)) | (1L << reds.get(right)));
                        }
                    }
                }
            }
            blueHits += blues.contains(draw.blueBall()) ? 1 : 0;
            redThreeHits += bestRed >= 3 ? 1 : 0;
            redFourHits += bestRed >= 4 ? 1 : 0;
            blueCoverage += blues.size();
            pairCoverage += pairs.size();
            tripleCoverage += triples.size();
        }

        Map<String, Object> summary() {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("cases", cases);
            summary.put("blueHitRate", blueHits / (double) cases);
            summary.put("atLeastThreeRedHitRate", redThreeHits / (double) cases);
            summary.put("atLeastFourRedHitRate", redFourHits / (double) cases);
            summary.put("averageBlueCoverage", blueCoverage / (double) cases);
            summary.put("averagePairCoverage", pairCoverage / (double) cases);
            summary.put("averageTripleCoverage", tripleCoverage / (double) cases);
            return summary;
        }

    }

}
