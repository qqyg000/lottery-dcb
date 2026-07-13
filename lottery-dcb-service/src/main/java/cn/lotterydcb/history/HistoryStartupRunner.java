package cn.lotterydcb.history;

import cn.lotterydcb.config.LotteryHistoryProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class HistoryStartupRunner implements ApplicationRunner {

    private final LotteryHistoryProperties properties;

    private final HistoryUpdateService updateService;

    public HistoryStartupRunner(
            LotteryHistoryProperties properties,
            HistoryUpdateService updateService
    ) {
        this.properties = properties;
        this.updateService = updateService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isUpdateEnabled()) {
            updateService.refresh();
        }
    }

}
