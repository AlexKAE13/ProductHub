package ru.eltex.producthub.importer;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** План импорта папки или загрузки: хранится на сервере между предпросмотром и подтверждением. */
public class ImportPlan {

    private final String token;
    private final String source;
    private final String createdBy;
    private final LocalDateTime createdAt = LocalDateTime.now();
    private final Path stagingDir;
    private final List<PlanRow> rows = new ArrayList<>();
    private final List<SkippedFile> skipped = new ArrayList<>();
    /** Артикул карточки SKU, из которой начата загрузка: после импорта на нее ведет кнопка возврата. */
    private String returnSku;

    public ImportPlan(String token, String source, String createdBy, Path stagingDir) {
        this.token = token;
        this.source = source;
        this.createdBy = createdBy;
        this.stagingDir = stagingDir;
    }

    public String getToken() {
        return token;
    }

    public String getSource() {
        return source;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Path getStagingDir() {
        return stagingDir;
    }

    public List<PlanRow> getRows() {
        return rows;
    }

    public List<SkippedFile> getSkipped() {
        return skipped;
    }

    public long countByAction(PlanRow.Action action) {
        return rows.stream().filter(r -> r.getAction() == action).count();
    }

    public long getCreateCount() {
        return countByAction(PlanRow.Action.CREATE);
    }

    public long getVersionCount() {
        return countByAction(PlanRow.Action.NEW_VERSION);
    }

    public long getSkipCount() {
        return countByAction(PlanRow.Action.SKIP);
    }

    public String getReturnSku() {
        return returnSku;
    }

    public void setReturnSku(String returnSku) {
        this.returnSku = returnSku;
    }
}
