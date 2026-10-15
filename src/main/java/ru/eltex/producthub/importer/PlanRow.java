package ru.eltex.producthub.importer;

import java.util.ArrayList;
import java.util.List;

/** Строка предпросмотра импорта: один будущий артефакт (или его новая версия). */
public class PlanRow {

    public enum Action {
        CREATE, NEW_VERSION, SKIP
    }

    // Поля, которые пользователь правит в предпросмотре
    private boolean include;
    private String title;
    private String type;
    private String language;
    private String status = "ACTUAL";
    private String skus;
    private String line;
    private String tags;
    private String contour;
    private Long ownerId;
    private String description;

    // Поля, которые определяет сканер
    private String key;
    private Action action;
    private Long existingId;
    private String note;
    private boolean skuFound;
    private final List<PlanFile> files = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void apply(RowEdit e) {
        if (action == Action.SKIP) {
            return;
        }
        this.include = e.isInclude();
        this.title = e.getTitle();
        this.type = e.getType();
        this.language = e.getLanguage();
        this.status = e.getStatus();
        this.skus = e.getSkus();
        this.line = e.getLine();
        this.tags = e.getTags();
        this.contour = e.getContour();
        this.ownerId = e.getOwnerId();
        this.description = e.getDescription();
    }

    public boolean isInclude() {
        return include;
    }

    public void setInclude(boolean include) {
        this.include = include;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSkus() {
        return skus;
    }

    public void setSkus(String skus) {
        this.skus = skus;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getContour() {
        return contour;
    }

    public void setContour(String contour) {
        this.contour = contour;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public Long getExistingId() {
        return existingId;
    }

    public void setExistingId(Long existingId) {
        this.existingId = existingId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isSkuFound() {
        return skuFound;
    }

    public void setSkuFound(boolean skuFound) {
        this.skuFound = skuFound;
    }

    public List<PlanFile> getFiles() {
        return files;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
