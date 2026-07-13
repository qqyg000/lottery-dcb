package cn.lotterydcb.controller;

import cn.lotterydcb.statistics.StatisticsResponse;
import cn.lotterydcb.statistics.StatisticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public StatisticsResponse analyze(
            @RequestParam(defaultValue = "100") @Min(10) @Max(1000) int lookback
    ) {
        return statisticsService.analyze(lookback);
    }

}
