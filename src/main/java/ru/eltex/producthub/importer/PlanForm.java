package ru.eltex.producthub.importer;

import java.util.ArrayList;
import java.util.List;

/** Форма предпросмотра: правки строк и режим проверки без записи (dry-run). */
public class PlanForm {

    private List<RowEdit> rows = new ArrayList<>();
    private boolean dryRun;

    public List<RowEdit> getRows() {
        return rows;
    }

    public void setRows(List<RowEdit> rows) {
        this.rows = rows;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
