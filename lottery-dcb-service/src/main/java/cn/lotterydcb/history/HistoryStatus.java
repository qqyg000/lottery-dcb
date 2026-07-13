package cn.lotterydcb.history;

public record HistoryStatus(
        String filePath,
        int recordCount,
        String latestIssue,
        String latestDrawDate,
        String dataUpdatedAt,
        boolean updateEnabled,
        boolean updating,
        String lastAttemptAt,
        String lastSuccessAt,
        int lastAddedCount,
        String lastError
) {

}
