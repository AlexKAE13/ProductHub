package ru.eltex.producthub.sku.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.security.Role;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.sku.domain.ProductLine;
import ru.eltex.producthub.sku.domain.SpecDefinition;
import ru.eltex.producthub.sku.repo.ProductLineRepository;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.repo.SpecDefinitionRepository;

/** Справочники с кэшем. Сброс кэша - {@link #evictAll()} после любого изменения справочников. */
@Service
public class ReferenceDataService {

    private final ProductLineRepository lines;
    private final SpecDefinitionRepository specs;
    private final SkuRepository skus;
    private final UserRepository users;

    public ReferenceDataService(ProductLineRepository lines, SpecDefinitionRepository specs, SkuRepository skus,
                                UserRepository users) {
        this.lines = lines;
        this.specs = specs;
        this.skus = skus;
        this.users = users;
    }

    @Cacheable(CacheConfig.LINES)
    @Transactional(readOnly = true)
    public List<ProductLine> lines() {
        return lines.findAllByOrderBySortOrderAscCodeAsc();
    }

    public List<String> families() {
        return lines().stream().map(ProductLine::getFamily).distinct().sorted().toList();
    }

    @Cacheable(CacheConfig.SPECS)
    @Transactional(readOnly = true)
    public List<SpecDefinition> specDefinitions() {
        return specs.findAllByOrderBySortOrderAsc();
    }

    @Cacheable(value = CacheConfig.SPECS, key = "'cat:' + #category")
    public List<SpecDefinition> specDefinitionsFor(String category) {
        return specs.findAllByOrderBySortOrderAsc().stream().filter(d -> d.appliesTo(category)).toList();
    }

    @Cacheable(CacheConfig.CATEGORIES)
    @Transactional(readOnly = true)
    public List<String> categories() {
        return skus.findCategories();
    }

    /** Продакты, которых можно назначить владельцем SKU. */
    @Cacheable(CacheConfig.OWNERS)
    @Transactional(readOnly = true)
    public List<User> owners() {
        return users.findActiveByRole(Role.PRODUCT);
    }

    @CacheEvict(cacheNames = {CacheConfig.LINES, CacheConfig.SPECS, CacheConfig.CATEGORIES, CacheConfig.OWNERS},
            allEntries = true)
    public void evictAll() {
    }
}
