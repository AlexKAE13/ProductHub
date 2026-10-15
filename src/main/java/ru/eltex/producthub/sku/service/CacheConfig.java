package ru.eltex.producthub.sku.service;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/** Кэш справочников: линейки, шаблоны характеристик, категории, владельцы (SPEC.md 8.1). */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String LINES = "lines";
    public static final String SPECS = "specDefinitions";
    public static final String CATEGORIES = "categories";
    public static final String OWNERS = "owners";
    public static final String TAGS = "tags";
    public static final String TYPES = "artifactTypes";
    public static final String SETTINGS = "userSettings";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(LINES, SPECS, CATEGORIES, OWNERS, TAGS, TYPES, SETTINGS);
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(100));
        return manager;
    }
}
