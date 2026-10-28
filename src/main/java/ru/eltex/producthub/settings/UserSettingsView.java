package ru.eltex.producthub.settings;

/** Настройки пользователя, готовые к показу в шаблонах и безопасные для кэша. */
public record UserSettingsView(
        MenuLayout menuLayout,
        boolean sidebarCollapsed,
        Density density,
        StartPage startPage,
        boolean catalogOnlyMine,
        boolean catalogIncludeEol,
        Look look) {

    public UserSettingsView(MenuLayout menuLayout, boolean sidebarCollapsed, Density density, StartPage startPage,
                            boolean catalogOnlyMine, boolean catalogIncludeEol) {
        this(menuLayout, sidebarCollapsed, density, startPage, catalogOnlyMine, catalogIncludeEol, Look.DEFAULT);
    }

    public UserSettingsView withLook(Look newLook) {
        return new UserSettingsView(menuLayout, sidebarCollapsed, density, startPage, catalogOnlyMine, catalogIncludeEol, newLook);
    }


    public static final UserSettingsView DEFAULTS = new UserSettingsView(MenuLayout.SIDE, false, Density.NORMAL,
            StartPage.HOME, false, false, Look.DEFAULT);

    public boolean isSide() {
        return menuLayout == MenuLayout.SIDE;
    }

    public boolean isCompact() {
        return density == Density.COMPACT;
    }

    /** CSS-классы для body. */
    public String bodyClasses() {
        StringBuilder sb = new StringBuilder(isSide() ? "layout-side" : "layout-top");
        if (isSide() && sidebarCollapsed) {
            sb.append(" sidebar-collapsed");
        }
        if (isCompact()) {
            sb.append(" density-compact");
        }
        if (look.isModern()) {
            sb.append(" theme-modern");
        }
        return sb.toString();
    }
}
