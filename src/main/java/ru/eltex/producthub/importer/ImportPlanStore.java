package ru.eltex.producthub.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Планы импорта в памяти. Живут два часа, потом удаляются вместе с временными файлами загрузки. */
@Component
public class ImportPlanStore {

    private static final long TTL_HOURS = 2;
    private final Map<String, ImportPlan> plans = new ConcurrentHashMap<>();

    public void put(ImportPlan plan) {
        plans.put(plan.getToken(), plan);
    }

    public ImportPlan get(String token) {
        return plans.get(token);
    }

    public void remove(String token) {
        ImportPlan plan = plans.remove(token);
        if (plan != null) {
            deleteStaging(plan);
        }
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000L, initialDelay = 30 * 60 * 1000L)
    public void expire() {
        LocalDateTime limit = LocalDateTime.now().minusHours(TTL_HOURS);
        plans.values().stream().filter(p -> p.getCreatedAt().isBefore(limit)).map(ImportPlan::getToken).toList()
                .forEach(this::remove);
    }

    private void deleteStaging(ImportPlan plan) {
        Path dir = plan.getStagingDir();
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // временные файлы, не критично
        }
    }
}
