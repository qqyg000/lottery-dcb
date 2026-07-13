package cn.lotterydcb.controller;

import cn.lotterydcb.history.DrawRecord;
import cn.lotterydcb.history.HistoryRepository;
import cn.lotterydcb.history.HistoryStatus;
import cn.lotterydcb.history.HistoryUpdateResult;
import cn.lotterydcb.history.HistoryUpdateService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryRepository repository;

    private final HistoryUpdateService updateService;

    public HistoryController(HistoryRepository repository, HistoryUpdateService updateService) {
        this.repository = repository;
        this.updateService = updateService;
    }

    @GetMapping
    public List<DrawRecord> latest(
            @RequestParam(defaultValue = "30") @Min(1) @Max(1000) int limit
    ) {
        return repository.findLatest(limit);
    }

    @GetMapping("/status")
    public HistoryStatus status() {
        return updateService.getStatus();
    }

    @PostMapping("/refresh")
    public HistoryUpdateResult refresh() {
        return updateService.refresh();
    }

}
