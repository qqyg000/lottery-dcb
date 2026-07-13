package cn.lotterydcb.history;

public record HistoryUpdateResult(
        boolean success,
        int addedCount,
        int totalCount,
        int pagesFetched,
        String message,
        String completedAt
) {

}
