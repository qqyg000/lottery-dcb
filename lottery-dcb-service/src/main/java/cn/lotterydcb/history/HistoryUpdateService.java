package cn.lotterydcb.history;

import cn.lotterydcb.config.LotteryHistoryProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HistoryUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryUpdateService.class);

    private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");

    private final LotteryHistoryProperties properties;

    private final HistoryRepository repository;

    private final HttpClient httpClient;

    private final AtomicBoolean updating = new AtomicBoolean(false);

    private volatile String lastAttemptAt;

    private volatile String lastSuccessAt;

    private volatile String lastError;

    private volatile int lastAddedCount;

    public HistoryUpdateService(LotteryHistoryProperties properties, HistoryRepository repository) {
        this.properties = properties;
        this.repository = repository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public HistoryUpdateResult refresh() {
        if (!properties.isUpdateEnabled()) {
            String completedAt = Instant.now().toString();
            return new HistoryUpdateResult(
                    false,
                    0,
                    repository.snapshot().getRecords().size(),
                    0,
                    "历史数据联网更新已关闭",
                    completedAt
            );
        }
        return refresh(this::fetchPageWithRetry);
    }

    HistoryUpdateResult refresh(PageFetcher pageFetcher) {
        if (!updating.compareAndSet(false, true)) {
            return new HistoryUpdateResult(
                    false,
                    0,
                    repository.snapshot().getRecords().size(),
                    0,
                    "历史数据正在更新，请稍后重试",
                    Instant.now().toString()
            );
        }

        lastAttemptAt = Instant.now().toString();
        lastError = null;
        int pagesFetched = 0;

        try {
            HistoryArchive archive = repository.snapshot();
            boolean initialSync = !archive.isInitialSyncCompleted();
            int maxPages = initialSync
                    ? Math.max(1, properties.getInitialMaxPages())
                    : Math.max(1, properties.getIncrementalMaxPages());
            String boundaryIssue = archive.getRecords().isEmpty()
                    ? null
                    : archive.getRecords().get(0).issue();

            List<DrawRecord> remoteRecords = new ArrayList<>();
            int declaredPageCount = 0;
            boolean reachedBoundary = false;
            boolean reachedEnd = false;

            for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
                RemotePage page = pageFetcher.fetch(pageNo);
                pagesFetched++;
                if (page.pageCount() > 0) {
                    declaredPageCount = page.pageCount();
                }
                remoteRecords.addAll(page.records());
                if (!initialSync && boundaryIssue != null && page.records().stream()
                        .anyMatch(record -> boundaryIssue.equals(record.issue()))) {
                    reachedBoundary = true;
                }
                if (page.records().isEmpty()
                        || (declaredPageCount > 0 && pageNo >= declaredPageCount)) {
                    reachedEnd = true;
                }
                if (reachedBoundary || reachedEnd) {
                    break;
                }
            }

            if (remoteRecords.isEmpty()) {
                throw new IOException("官方接口未返回有效开奖记录");
            }
            if (initialSync && !reachedEnd) {
                throw new IOException("首次同步未拉取到最后一页，请增大 initial-max-pages 后重试");
            }
            if (!initialSync && !reachedBoundary && !reachedEnd) {
                throw new IOException("增量同步未衔接到本地边界期号，请增大 incremental-max-pages 后重试");
            }

            int added = repository.mergeAndSave(remoteRecords, initialSync && reachedEnd);
            lastAddedCount = added;
            lastSuccessAt = Instant.now().toString();
            int total = repository.snapshot().getRecords().size();
            String message = added > 0
                    ? "历史数据更新成功，新增 " + added + " 期"
                    : "历史数据已是最新";
            LOGGER.info("{}，请求页数={}，总记录数={}", message, pagesFetched, total);
            return new HistoryUpdateResult(true, added, total, pagesFetched, message, lastSuccessAt);
        } catch (Exception exception) {
            lastAddedCount = 0;
            lastError = conciseMessage(exception);
            LOGGER.warn("双色球历史数据更新失败，将继续使用本地配置文件: {}", lastError);
            return new HistoryUpdateResult(
                    false,
                    0,
                    repository.snapshot().getRecords().size(),
                    pagesFetched,
                    "更新失败，已继续使用本地历史数据: " + lastError,
                    Instant.now().toString()
            );
        } finally {
            updating.set(false);
        }
    }

    public HistoryStatus getStatus() {
        HistoryArchive archive = repository.snapshot();
        DrawRecord latest = archive.getRecords().stream().findFirst().orElse(null);
        return new HistoryStatus(
                properties.getFilePath().toAbsolutePath().normalize().toString(),
                archive.getRecords().size(),
                latest == null ? null : latest.issue(),
                latest == null ? null : latest.drawDate(),
                archive.getUpdatedAt(),
                properties.isUpdateEnabled(),
                updating.get(),
                lastAttemptAt,
                lastSuccessAt,
                lastAddedCount,
                lastError
        );
    }

    private RemotePage fetchPageWithRetry(int pageNo) throws IOException, InterruptedException {
        Exception lastException = null;
        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return fetchPage(pageNo);
            } catch (IOException | InterruptedException exception) {
                lastException = exception;
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw (InterruptedException) exception;
                }
                if (attempt < attempts) {
                    Thread.sleep(properties.getRetryDelay().toMillis());
                }
            }
        }
        throw new IOException("请求官方开奖接口失败", lastException);
    }

    private RemotePage fetchPage(int pageNo) throws IOException, InterruptedException {
        URI uri = buildUri(pageNo);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", properties.getReferer())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/150 Safari/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("官方接口 HTTP 状态码 " + response.statusCode());
        }
        return parseRemotePage(response.body());
    }

    private URI buildUri(int pageNo) {
        String query = "name=ssq"
                + "&issueCount="
                + "&issueStart="
                + "&issueEnd="
                + "&dayStart="
                + "&dayEnd="
                + "&pageNo=" + pageNo
                + "&pageSize=" + Math.max(1, Math.min(properties.getPageSize(), 1000))
                + "&week="
                + "&systemType=" + encode("PC");
        return URI.create(properties.getSourceUrl() + "?" + query);
    }

    private RemotePage parseRemotePage(String body) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            String state = text(root, "state");
            if (state != null && !state.isBlank() && !"0".equals(state)) {
                throw new IOException("官方接口返回失败状态: " + state + " " + text(root, "message"));
            }

            int pageCount = integer(root, "pageCount", 0);
            JsonArray result = root.has("result") && root.get("result").isJsonArray()
                    ? root.getAsJsonArray("result")
                    : new JsonArray();
            List<DrawRecord> records = new ArrayList<>();
            for (JsonElement element : result) {
                if (!element.isJsonObject()) {
                    continue;
                }
                DrawRecord record = toRecord(element.getAsJsonObject());
                if (record != null && record.isValid()) {
                    records.add(record);
                }
            }
            return new RemotePage(pageCount, records);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("官方接口响应结构无法解析", exception);
        }
    }

    private DrawRecord toRecord(JsonObject item) {
        String issue = text(item, "code");
        String rawDate = text(item, "date");
        String red = text(item, "red");
        String blue = text(item, "blue");
        if (issue == null || rawDate == null || red == null || blue == null) {
            return null;
        }

        Matcher matcher = DATE_PATTERN.matcher(rawDate);
        String drawDate = matcher.find() ? matcher.group(1) : rawDate;
        List<Integer> redBalls = parseNumbers(red);
        List<Integer> blueBalls = parseNumbers(blue);
        if (blueBalls.isEmpty()) {
            return null;
        }
        return new DrawRecord(issue.trim(), drawDate.trim(), redBalls, blueBalls.get(0));
    }

    private List<Integer> parseNumbers(String value) {
        List<Integer> numbers = new ArrayList<>();
        for (String part : value.trim().split("[,，\\s]+")) {
            if (!part.isBlank()) {
                numbers.add(Integer.parseInt(part));
            }
        }
        return numbers;
    }

    private String text(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private int integer(JsonObject object, String field, int defaultValue) {
        JsonElement element = object.get(field);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsInt();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String conciseMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    interface PageFetcher {

        RemotePage fetch(int pageNo) throws IOException, InterruptedException;

    }

    record RemotePage(int pageCount, List<DrawRecord> records) {

    }

}
