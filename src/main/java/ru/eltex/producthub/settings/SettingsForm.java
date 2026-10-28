package ru.eltex.producthub.settings;

/** Форма страницы "Настройки". */
public class SettingsForm {

    private MenuLayout menuLayout = MenuLayout.SIDE;
    private boolean sidebarCollapsed;
    private Density density = Density.NORMAL;
    private StartPage startPage = StartPage.HOME;
    private boolean catalogOnlyMine;
    private boolean catalogIncludeEol;
    private UiTheme uiTheme = Look.DEFAULT.theme();
    private ColorMode colorMode = Look.DEFAULT.mode();
    private Palette palette = Look.DEFAULT.palette();
    private Background background = Background.MESH;
    private String wallpaper = "dunes";
    private int wallBlur = 18;
    private int wallDim;

    public static SettingsForm from(UserSettingsView v) {
        SettingsForm f = new SettingsForm();
        f.menuLayout = v.menuLayout();
        f.sidebarCollapsed = v.sidebarCollapsed();
        f.density = v.density();
        f.startPage = v.startPage();
        f.catalogOnlyMine = v.catalogOnlyMine();
        f.catalogIncludeEol = v.catalogIncludeEol();
        Look l = v.look();
        f.uiTheme = l.theme();
        f.colorMode = l.mode();
        f.palette = l.palette();
        f.background = l.background();
        f.wallpaper = l.wallpaper();
        f.wallBlur = l.blur();
        f.wallDim = l.dim();
        return f;
    }

    /** Ключ загруженных обоев берется из сохраненных настроек: форма его не передает. */
    public UserSettingsView toView(String wallpaperKey) {
        Look look = new Look(uiTheme, colorMode, palette, background, wallpaper, wallpaperKey, wallBlur, wallDim);
        return new UserSettingsView(menuLayout, sidebarCollapsed, density, startPage, catalogOnlyMine, catalogIncludeEol,
                look);
    }

    public MenuLayout getMenuLayout() {
        return menuLayout;
    }

    public void setMenuLayout(MenuLayout menuLayout) {
        this.menuLayout = menuLayout;
    }

    public boolean isSidebarCollapsed() {
        return sidebarCollapsed;
    }

    public void setSidebarCollapsed(boolean sidebarCollapsed) {
        this.sidebarCollapsed = sidebarCollapsed;
    }

    public Density getDensity() {
        return density;
    }

    public void setDensity(Density density) {
        this.density = density;
    }

    public StartPage getStartPage() {
        return startPage;
    }

    public void setStartPage(StartPage startPage) {
        this.startPage = startPage;
    }

    public boolean isCatalogOnlyMine() {
        return catalogOnlyMine;
    }

    public void setCatalogOnlyMine(boolean catalogOnlyMine) {
        this.catalogOnlyMine = catalogOnlyMine;
    }

    public boolean isCatalogIncludeEol() {
        return catalogIncludeEol;
    }

    public void setCatalogIncludeEol(boolean catalogIncludeEol) {
        this.catalogIncludeEol = catalogIncludeEol;
    }

    public UiTheme getUiTheme() {
        return uiTheme;
    }

    public void setUiTheme(UiTheme uiTheme) {
        this.uiTheme = uiTheme;
    }

    public ColorMode getColorMode() {
        return colorMode;
    }

    public void setColorMode(ColorMode colorMode) {
        this.colorMode = colorMode;
    }

    public Palette getPalette() {
        return palette;
    }

    public void setPalette(Palette palette) {
        this.palette = palette;
    }

    public Background getBackground() {
        return background;
    }

    public void setBackground(Background background) {
        this.background = background;
    }

    public String getWallpaper() {
        return wallpaper;
    }

    public void setWallpaper(String wallpaper) {
        this.wallpaper = wallpaper;
    }

    public int getWallBlur() {
        return wallBlur;
    }

    public void setWallBlur(int wallBlur) {
        this.wallBlur = wallBlur;
    }

    public int getWallDim() {
        return wallDim;
    }

    public void setWallDim(int wallDim) {
        this.wallDim = wallDim;
    }
}
