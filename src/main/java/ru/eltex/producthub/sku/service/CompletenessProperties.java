package ru.eltex.producthub.sku.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Веса частей заполненности (SPEC.md 5.4). Задаются в application.yml. */
@ConfigurationProperties(prefix = "producthub.completeness")
public class CompletenessProperties {

    private int identification = 20;
    private int lifecycle = 15;
    private int specs = 35;
    private int commercial = 10;
    private int datasheets = 10;
    private int firmware = 5;
    private int photos = 5;
    private int commercialMaxAgeDays = 30;
    private String datasheetType = "Даташит";
    private String photoType = "Фото";

    public int getIdentification() {
        return identification;
    }

    public void setIdentification(int identification) {
        this.identification = identification;
    }

    public int getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(int lifecycle) {
        this.lifecycle = lifecycle;
    }

    public int getSpecs() {
        return specs;
    }

    public void setSpecs(int specs) {
        this.specs = specs;
    }

    public int getCommercial() {
        return commercial;
    }

    public void setCommercial(int commercial) {
        this.commercial = commercial;
    }

    public int getDatasheets() {
        return datasheets;
    }

    public void setDatasheets(int datasheets) {
        this.datasheets = datasheets;
    }

    public int getFirmware() {
        return firmware;
    }

    public void setFirmware(int firmware) {
        this.firmware = firmware;
    }

    public int getPhotos() {
        return photos;
    }

    public void setPhotos(int photos) {
        this.photos = photos;
    }

    public int getCommercialMaxAgeDays() {
        return commercialMaxAgeDays;
    }

    public void setCommercialMaxAgeDays(int commercialMaxAgeDays) {
        this.commercialMaxAgeDays = commercialMaxAgeDays;
    }

    public String getDatasheetType() {
        return datasheetType;
    }

    public void setDatasheetType(String datasheetType) {
        this.datasheetType = datasheetType;
    }

    public String getPhotoType() {
        return photoType;
    }

    public void setPhotoType(String photoType) {
        this.photoType = photoType;
    }
}
