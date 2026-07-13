package cn.lotterydcb.history;

import java.util.ArrayList;
import java.util.List;

public class HistoryArchive {

    private int schemaVersion = 1;

    private String source = "中国福利彩票官网";

    private String updatedAt;

    private boolean initialSyncCompleted;

    private List<DrawRecord> records = new ArrayList<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isInitialSyncCompleted() {
        return initialSyncCompleted;
    }

    public void setInitialSyncCompleted(boolean initialSyncCompleted) {
        this.initialSyncCompleted = initialSyncCompleted;
    }

    public List<DrawRecord> getRecords() {
        return records;
    }

    public void setRecords(List<DrawRecord> records) {
        this.records = records == null ? new ArrayList<>() : new ArrayList<>(records);
    }

}
