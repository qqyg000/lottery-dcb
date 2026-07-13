package cn.lotterydcb.history;

import cn.lotterydcb.config.LotteryHistoryProperties;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryUpdateServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void initialSyncShouldReachTheReportedLastPageBeforeCompletion() {
        TestContext context = createContext();
        context.properties().setInitialMaxPages(5);

        HistoryUpdateResult result = context.service().refresh(pageNo -> switch (pageNo) {
            case 1 -> page(3, record("2026081"));
            case 2 -> page(3, record("2026080"));
            case 3 -> page(3, record("2026079"));
            default -> page(3);
        });

        assertTrue(result.success());
        assertEquals(3, result.pagesFetched());
        assertEquals(3, result.addedCount());
        assertTrue(context.repository().snapshot().isInitialSyncCompleted());
    }

    @Test
    void initialSyncShouldNotSaveAPartialArchiveAtTheSafetyLimit() {
        TestContext context = createContext();
        context.properties().setInitialMaxPages(2);
        int originalCount = context.repository().snapshot().getRecords().size();

        HistoryUpdateResult result = context.service().refresh(pageNo -> page(3, record("202608" + pageNo)));

        assertFalse(result.success());
        assertEquals(originalCount, context.repository().snapshot().getRecords().size());
        assertFalse(context.repository().snapshot().isInitialSyncCompleted());
    }

    @Test
    void incrementalSyncShouldContinueUntilTheOriginalBoundaryIssue() {
        TestContext context = createContext();
        context.repository().mergeAndSave(List.of(), true);
        context.properties().setIncrementalMaxPages(5);

        HistoryUpdateResult result = context.service().refresh(pageNo -> switch (pageNo) {
            case 1 -> page(3, record("2026080"), record("2026079"));
            case 2 -> page(3, record("2026078"));
            default -> page(3);
        });

        assertTrue(result.success());
        assertEquals(2, result.pagesFetched());
        assertEquals(2, result.addedCount());
        assertEquals("2026080", context.repository().snapshot().getRecords().get(0).issue());
    }

    @Test
    void disabledUpdatesShouldReturnWithoutNetworkAccess() {
        TestContext context = createContext();
        context.properties().setUpdateEnabled(false);

        HistoryUpdateResult result = context.service().refresh();

        assertFalse(result.success());
        assertEquals(0, result.pagesFetched());
        assertEquals("历史数据联网更新已关闭", result.message());
    }

    private TestContext createContext() {
        LotteryHistoryProperties properties = new LotteryHistoryProperties();
        properties.setFilePath(tempDirectory.resolve("ssq-history.json"));
        HistoryRepository repository = new HistoryRepository(
                properties,
                new GsonBuilder().setPrettyPrinting().create()
        );
        repository.initialize();
        return new TestContext(properties, repository, new HistoryUpdateService(properties, repository));
    }

    private HistoryUpdateService.RemotePage page(int pageCount, DrawRecord... records) {
        return new HistoryUpdateService.RemotePage(pageCount, List.of(records));
    }

    private DrawRecord record(String issue) {
        return new DrawRecord(issue, "2026-07-12", List.of(2, 7, 12, 18, 25, 31), 9);
    }

    private record TestContext(
            LotteryHistoryProperties properties,
            HistoryRepository repository,
            HistoryUpdateService service
    ) {

    }

}
