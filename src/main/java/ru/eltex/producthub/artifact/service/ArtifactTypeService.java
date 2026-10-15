package ru.eltex.producthub.artifact.service;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.artifact.domain.ArtifactType;
import ru.eltex.producthub.artifact.repo.ArtifactTypeRepository;
import ru.eltex.producthub.sku.service.CacheConfig;

@Service
public class ArtifactTypeService {

    private final ArtifactTypeRepository types;

    public ArtifactTypeService(ArtifactTypeRepository types) {
        this.types = types;
    }

    @Cacheable(CacheConfig.TYPES)
    @Transactional(readOnly = true)
    public List<ArtifactType> all() {
        return types.findAllByOrderBySortOrderAscNameAsc();
    }

    public Optional<ArtifactType> byName(String name) {
        return all().stream().filter(t -> t.getName().equalsIgnoreCase(name == null ? "" : name.trim())).findFirst();
    }

    public Optional<ArtifactType> byId(Long id) {
        return all().stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    @CacheEvict(cacheNames = CacheConfig.TYPES, allEntries = true)
    public void evict() {
    }
}
