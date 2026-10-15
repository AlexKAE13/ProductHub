package ru.eltex.producthub.settings;

/** Куда попадать сразу после входа. */
public enum StartPage {
    HOME("/"), CATALOG("/catalog"), ARTIFACTS("/artifacts");

    private final String path;

    StartPage(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
