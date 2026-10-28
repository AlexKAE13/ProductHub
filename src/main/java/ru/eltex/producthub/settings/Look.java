package ru.eltex.producthub.settings;

/**
 * Внешний вид личного кабинета: тема, схема, фон и обои.
 *
 * @param wallpaper  "dunes", "peaks", "aurora", "firs", "sage", "slate" (встроенные) или "custom" (загруженные пользователем)
 * @param wallpaperKey ключ загруженного файла в хранилище, если он есть
 * @param blur       размытие обоев, 0-40 px
 * @param dim        затемнение (светлая тема - осветление) обоев, 0-80 %
 */
public record Look(
        UiTheme theme,
        ColorMode mode,
        Palette palette,
        Background background,
        String wallpaper,
        String wallpaperKey,
        int blur,
        int dim) {

    public static final Look DEFAULT = new Look(UiTheme.MODERN, ColorMode.LIGHT, Palette.PINE, Background.MESH,
            "dunes", null, 18, 0);

    public static final java.util.Set<String> BUILT_IN = java.util.Set.of("dunes", "peaks", "aurora", "firs", "sage", "slate");

    public boolean isModern() {
        return theme == UiTheme.MODERN;
    }

    /** Нормализует значения (на случай ручной подделки формы). */
    public Look sanitized() {
        String wall = wallpaper;
        if (wall == null || !(BUILT_IN.contains(wall) || ("custom".equals(wall) && wallpaperKey != null))) {
            wall = "dunes";
        }
        return new Look(theme == null ? DEFAULT.theme() : theme, mode == null ? DEFAULT.mode() : mode,
                palette == null ? DEFAULT.palette() : palette, background == null ? Background.MESH : background, wall,
                wallpaperKey, Math.max(0, Math.min(40, blur)), Math.max(0, Math.min(80, dim)));
    }

    /** Адрес картинки обоев для CSS. Версия в адресе сбрасывает кэш браузера при замене файла. */
    public String wallpaperUrl() {
        if ("custom".equals(wallpaper) && wallpaperKey != null) {
            return "/settings/wallpaper?v=" + wallpaperKey.substring(0, Math.min(8, wallpaperKey.length()));
        }
        return "/img/wallpapers/" + (BUILT_IN.contains(wallpaper) ? wallpaper : "dunes") + ".svg";
    }
}
