package ru.eltex.producthub.firmware.service;

/** Форма прошивки. Файл передается отдельно. */
public class FirmwareForm {

    private String version;
    private String releaseDate;
    private String status = "RELEASE";
    private String releaseNotes;
    private String externalUrl;
    private String skus;
    private boolean publicRelease;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getSkus() {
        return skus;
    }

    public void setSkus(String skus) {
        this.skus = skus;
    }

    public boolean isPublicRelease() {
        return publicRelease;
    }

    public void setPublicRelease(boolean publicRelease) {
        this.publicRelease = publicRelease;
    }
}
