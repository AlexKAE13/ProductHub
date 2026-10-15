package ru.eltex.producthub.settings;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.sku.service.CacheConfig;

/** Личные настройки интерфейса. Хранятся в БД, чтобы одинаково работали на разных компьютерах. */
@Service
public class UserSettingsService {

    private final UserSettingRepository repository;

    public UserSettingsService(UserSettingRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = CacheConfig.SETTINGS, key = "#userId")
    @Transactional(readOnly = true)
    public UserSettingsView get(Long userId) {
        return repository.findById(userId).map(UserSettingsService::view).orElse(UserSettingsView.DEFAULTS);
    }

    @CacheEvict(value = CacheConfig.SETTINGS, key = "#userId")
    @Transactional
    public UserSettingsView save(Long userId, UserSettingsView values) {
        UserSetting s = repository.findById(userId).orElseGet(() -> new UserSetting(userId));
        s.setMenuLayout(values.menuLayout());
        s.setSidebarCollapsed(values.sidebarCollapsed());
        s.setDensity(values.density());
        s.setStartPage(values.startPage());
        s.setCatalogOnlyMine(values.catalogOnlyMine());
        s.setCatalogIncludeEol(values.catalogIncludeEol());
        s.setLook(values.look().sanitized());
        return view(repository.save(s));
    }

    /** Запоминает только состояние сайдбара (кнопка свернуть или развернуть). */
    @CacheEvict(value = CacheConfig.SETTINGS, key = "#userId")
    @Transactional
    public void setSidebarCollapsed(Long userId, boolean collapsed) {
        UserSetting s = repository.findById(userId).orElseGet(() -> new UserSetting(userId));
        s.setSidebarCollapsed(collapsed);
        repository.save(s);
    }

    /** Быстрое переключение светлой и темной темы кнопкой в меню: меняется только режим. */
    @CacheEvict(value = CacheConfig.SETTINGS, key = "#userId")
    @Transactional
    public void setColorMode(Long userId, ColorMode mode) {
        UserSetting s = repository.findById(userId).orElseGet(() -> new UserSetting(userId));
        Look l = s.getLook();
        s.setLook(new Look(l.theme(), mode, l.palette(), l.background(), l.wallpaper(), l.wallpaperKey(), l.blur(), l.dim()));
        repository.save(s);
    }

    static UserSettingsView view(UserSetting s) {
        return new UserSettingsView(s.getMenuLayout(), s.isSidebarCollapsed(), s.getDensity(), s.getStartPage(),
                s.isCatalogOnlyMine(), s.isCatalogIncludeEol(), s.getLook());
    }
}
