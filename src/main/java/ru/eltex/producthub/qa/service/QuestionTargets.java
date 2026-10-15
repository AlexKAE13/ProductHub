package ru.eltex.producthub.qa.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.artifact.repo.ArtifactRepository;
import ru.eltex.producthub.firmware.repo.FirmwareRepository;
import ru.eltex.producthub.qa.domain.TargetType;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.SkuRepository;

/** Определяет, о каком объекте вопрос: владелец, название, адрес страницы и SKU (для фильтров задач). */
@Component
public class QuestionTargets {

    private final SkuRepository skus;
    private final ArtifactRepository artifacts;
    private final FirmwareRepository firmware;
    private final UserRepository users;

    public QuestionTargets(SkuRepository skus, ArtifactRepository artifacts, FirmwareRepository firmware,
                           UserRepository users) {
        this.skus = skus;
        this.artifacts = artifacts;
        this.firmware = firmware;
        this.users = users;
    }

    public record Target(TargetType type, Long id, String label, String url, User owner, Long skuId) {
    }

    @Transactional(readOnly = true)
    public Optional<Target> resolve(TargetType type, Long id) {
        return switch (type) {
            case SKU -> skus.findCardById(id).map(s -> new Target(type, id, s.getModel(), "/sku/" + s.getModel(),
                    s.getOwner(), s.getId()));
            case ARTIFACT -> artifacts.findCardById(id).map(a -> {
                List<Long> skuIds = artifacts.findSkuIds(id);
                return new Target(type, id, a.getTitle(), "/artifacts/" + id, a.getOwner(),
                        skuIds.isEmpty() ? null : skuIds.get(0));
            });
            case FIRMWARE -> firmware.findActiveById(id).map(f -> {
                List<Long> skuIds = firmware.findSkuIds(id);
                User owner = users.findByLoginAndActiveTrue(f.getPublishedBy()).orElseGet(() -> skuIds.isEmpty()
                        ? null : skus.findCardById(skuIds.get(0)).map(Sku::getOwner).orElse(null));
                return new Target(type, id, "Прошивка " + f.getVersion(), "/firmware/" + id, owner,
                        skuIds.isEmpty() ? null : skuIds.get(0));
            });
        };
    }
}
