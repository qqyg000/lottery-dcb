package cn.lotterydcb.statistics;

import cn.lotterydcb.history.DrawRecord;
import cn.lotterydcb.history.HistoryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class StatisticsService {

    private final HistoryRepository historyRepository;

    public StatisticsService(HistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public StatisticsResponse analyze(int requestedLookback) {
        List<DrawRecord> all = historyRepository.snapshot().getRecords();
        int lookback = Math.min(Math.max(10, requestedLookback), all.size());
        List<DrawRecord> window = all.stream().limit(lookback).toList();
        List<NumberFrequency> red = frequencies(window, 33, true);
        List<NumberFrequency> blue = frequencies(window, 16, false);
        List<Integer> active = red.stream()
                .sorted(Comparator.comparingInt(NumberFrequency::count).reversed()
                        .thenComparingInt(NumberFrequency::number))
                .limit(6)
                .map(NumberFrequency::number)
                .toList();
        List<Integer> quiet = red.stream()
                .sorted(Comparator.comparingInt(NumberFrequency::count)
                        .thenComparing(Comparator.comparingInt(NumberFrequency::missingDraws).reversed()))
                .limit(6)
                .map(NumberFrequency::number)
                .toList();
        return new StatisticsResponse(
                all.size(),
                lookback,
                red,
                blue,
                active,
                quiet,
                "频次只描述历史样本，不代表未来开奖倾向"
        );
    }

    private List<NumberFrequency> frequencies(List<DrawRecord> records, int maxNumber, boolean redBall) {
        int[] counts = new int[maxNumber + 1];
        int[] missing = new int[maxNumber + 1];
        java.util.Arrays.fill(missing, records.size());
        for (int drawIndex = 0; drawIndex < records.size(); drawIndex++) {
            DrawRecord record = records.get(drawIndex);
            if (redBall) {
                for (int number : record.redBalls()) {
                    counts[number]++;
                    if (missing[number] == records.size()) {
                        missing[number] = drawIndex;
                    }
                }
            } else {
                int number = record.blueBall();
                counts[number]++;
                if (missing[number] == records.size()) {
                    missing[number] = drawIndex;
                }
            }
        }

        List<NumberFrequency> result = new ArrayList<>();
        for (int number = 1; number <= maxNumber; number++) {
            double rate = records.isEmpty() ? 0.0 : (double) counts[number] / records.size();
            result.add(new NumberFrequency(number, counts[number], round(rate, 4), missing[number]));
        }
        return List.copyOf(result);
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

}
