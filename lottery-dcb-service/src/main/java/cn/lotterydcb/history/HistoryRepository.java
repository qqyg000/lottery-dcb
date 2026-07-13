package cn.lotterydcb.history;

import cn.lotterydcb.config.LotteryHistoryProperties;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Repository
public class HistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryRepository.class);

    private static final String SEED_RESOURCE = "data/ssq-history.seed.json";

    private final LotteryHistoryProperties properties;

    private final Gson gson;

    private final AtomicReference<HistoryArchive> archiveRef = new AtomicReference<>();

    public HistoryRepository(LotteryHistoryProperties properties, Gson gson) {
        this.properties = properties;
        this.gson = gson;
    }

    @PostConstruct
    public void initialize() {
        Path filePath = properties.getFilePath().toAbsolutePath().normalize();
        try {
            Files.createDirectories(filePath.getParent());
            HistoryArchive archive = Files.exists(filePath) ? read(filePath) : readSeed();
            normalize(archive);
            archiveRef.set(archive);
            if (!Files.exists(filePath)) {
                writeAtomically(filePath, archive);
            }
            LOGGER.info("双色球历史数据已加载，文件={}，记录数={}", filePath, archive.getRecords().size());
        } catch (Exception exception) {
            throw new IllegalStateException("历史数据初始化失败: " + filePath, exception);
        }
    }

    public HistoryArchive snapshot() {
        HistoryArchive source = archiveRef.get();
        HistoryArchive copy = new HistoryArchive();
        copy.setSchemaVersion(source.getSchemaVersion());
        copy.setSource(source.getSource());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setInitialSyncCompleted(source.isInitialSyncCompleted());
        copy.setRecords(List.copyOf(source.getRecords()));
        return copy;
    }

    public List<DrawRecord> findLatest(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return snapshot().getRecords().stream().limit(safeLimit).toList();
    }

    public synchronized int mergeAndSave(List<DrawRecord> incoming, boolean initialSyncCompleted) {
        HistoryArchive current = snapshot();
        Map<String, DrawRecord> byIssue = new LinkedHashMap<>();
        current.getRecords().forEach(record -> byIssue.put(record.issue(), record));
        int before = byIssue.size();
        incoming.stream()
                .filter(DrawRecord::isValid)
                .forEach(record -> byIssue.put(record.issue(), record));

        List<DrawRecord> merged = byIssue.values().stream()
                .sorted(Comparator.comparing(DrawRecord::issue).reversed())
                .toList();
        current.setRecords(merged);
        current.setUpdatedAt(Instant.now().toString());
        current.setInitialSyncCompleted(current.isInitialSyncCompleted() || initialSyncCompleted);

        try {
            writeAtomically(properties.getFilePath().toAbsolutePath().normalize(), current);
            archiveRef.set(current);
            return byIssue.size() - before;
        } catch (IOException exception) {
            throw new IllegalStateException("历史数据配置文件写入失败", exception);
        }
    }

    private HistoryArchive read(Path filePath) throws IOException {
        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            HistoryArchive archive = gson.fromJson(reader, HistoryArchive.class);
            if (archive == null) {
                throw new IOException("历史数据配置文件内容为空");
            }
            return archive;
        } catch (Exception exception) {
            Path brokenPath = filePath.resolveSibling(filePath.getFileName() + ".broken-" + System.currentTimeMillis());
            Files.move(filePath, brokenPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("历史数据配置文件损坏，已移动到 {} 并恢复 seed 数据", brokenPath, exception);
            return readSeed();
        }
    }

    private HistoryArchive readSeed() throws IOException {
        ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            HistoryArchive archive = gson.fromJson(reader, HistoryArchive.class);
            if (archive == null) {
                throw new IOException("seed 历史数据为空");
            }
            return archive;
        }
    }

    private void normalize(HistoryArchive archive) {
        List<DrawRecord> validRecords = archive.getRecords() == null
                ? new ArrayList<>()
                : archive.getRecords().stream()
                .filter(DrawRecord::isValid)
                .collect(java.util.stream.Collectors.toMap(
                        DrawRecord::issue,
                        record -> record,
                        (left, right) -> right
                ))
                .values().stream()
                .sorted(Comparator.comparing(DrawRecord::issue).reversed())
                .toList();
        archive.setSchemaVersion(1);
        archive.setRecords(validRecords);
    }

    private void writeAtomically(Path filePath, HistoryArchive archive) throws IOException {
        Files.createDirectories(filePath.getParent());
        Path tempFile = Files.createTempFile(filePath.getParent(), "ssq-history-", ".tmp");
        try {
            Files.writeString(tempFile, gson.toJson(archive), StandardCharsets.UTF_8);
            try {
                Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

}
