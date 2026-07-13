package cn.lotterydcb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "lottery.history")
public class LotteryHistoryProperties {

    private Path filePath = Path.of("./config/ssq-history.json");

    private String sourceUrl = "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice";

    private String referer = "https://www.cwl.gov.cn/ygkj/wqkjgg/";

    private boolean updateEnabled = true;

    private int pageSize = 100;

    private int initialMaxPages = 100;

    private int incrementalMaxPages = 100;

    private int maxRetries = 2;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration requestTimeout = Duration.ofSeconds(12);

    private Duration retryDelay = Duration.ofMillis(600);

    public Path getFilePath() {
        return filePath;
    }

    public void setFilePath(Path filePath) {
        this.filePath = filePath;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public boolean isUpdateEnabled() {
        return updateEnabled;
    }

    public void setUpdateEnabled(boolean updateEnabled) {
        this.updateEnabled = updateEnabled;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getInitialMaxPages() {
        return initialMaxPages;
    }

    public void setInitialMaxPages(int initialMaxPages) {
        this.initialMaxPages = initialMaxPages;
    }

    public int getIncrementalMaxPages() {
        return incrementalMaxPages;
    }

    public void setIncrementalMaxPages(int incrementalMaxPages) {
        this.incrementalMaxPages = incrementalMaxPages;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

}
