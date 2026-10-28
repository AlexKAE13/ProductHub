package ru.eltex.producthub.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_setting")
public class UserSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_layout", nullable = false)
    private MenuLayout menuLayout = MenuLayout.SIDE;

    @Column(name = "sidebar_collapsed", nullable = false)
    private boolean sidebarCollapsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Density density = Density.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_page", nullable = false)
    private StartPage startPage = StartPage.HOME;

    @Column(name = "catalog_only_mine", nullable = false)
    private boolean catalogOnlyMine;

    @Column(name = "catalog_include_eol", nullable = false)
    private boolean catalogIncludeEol;

    @Enumerated(EnumType.STRING)
    @Column(name = "ui_theme", nullable = false)
    private UiTheme uiTheme = Look.DEFAULT.theme();

    @Enumerated(EnumType.STRING)
    @Column(name = "color_mode", nullable = false)
    private ColorMode colorMode = Look.DEFAULT.mode();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Palette palette = Look.DEFAULT.palette();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Background background = Background.MESH;

    @Column(nullable = false)
    private String wallpaper = "dunes";

    @Column(name = "wallpaper_key")
    private String wallpaperKey;

    @Column(name = "wall_blur", nullable = false)
    private int wallBlur = 18;

    @Column(name = "wall_dim", nullable = false)
    private int wallDim;

    protected UserSetting() {
    }

    public UserSetting(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
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

    public Look getLook() {
        return new Look(uiTheme, colorMode, palette, background, wallpaper, wallpaperKey, wallBlur, wallDim);
    }

    public void setLook(Look look) {
        this.uiTheme = look.theme();
        this.colorMode = look.mode();
        this.palette = look.palette();
        this.background = look.background();
        this.wallpaper = look.wallpaper();
        this.wallpaperKey = look.wallpaperKey();
        this.wallBlur = look.blur();
        this.wallDim = look.dim();
    }
}
