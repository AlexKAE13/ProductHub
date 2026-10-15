package ru.eltex.producthub.sku.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.sku.domain.ProductLine;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.ProductLineRepository;

/**
 * Распределение владельцев по семействам: все названные в настройках люди становятся совладельцами SKU семейства,
 * а основной владелец (получатель автоматических задач и вопросов) выбирается по кругу по линейкам семейства.
 */
@Component
public class SkuOwners {

    private final OwnerProperties props;
    private final UserRepository users;
    private final ProductLineRepository lines;

    public SkuOwners(OwnerProperties props, UserRepository users, ProductLineRepository lines) {
        this.props = props;
        this.users = users;
        this.lines = lines;
    }

    /** Владельцы семейства из настроек (только существующие активные пользователи). */
    public List<User> familyOwners(String family) {
        List<User> out = new ArrayList<>();
        for (String login : props.loginsFor(family)) {
            users.findByLoginAndActiveTrue(login).ifPresent(out::add);
        }
        return out;
    }

    /** Основной владелец линейки: по кругу среди владельцев семейства в порядке линеек. Пусто, если семейство не настроено. */
    public User primaryFor(ProductLine line) {
        List<User> group = familyOwners(line.getFamily());
        if (group.isEmpty()) {
            return null;
        }
        List<ProductLine> familyLines = lines.findAllByOrderBySortOrderAscCodeAsc().stream()
                .filter(l -> l.getFamily().equalsIgnoreCase(line.getFamily())).toList();
        int index = 0;
        for (int i = 0; i < familyLines.size(); i++) {
            if (familyLines.get(i).getId().equals(line.getId())) {
                index = i;
                break;
            }
        }
        return group.get(index % group.size());
    }

    /** Применяет распределение к новому SKU. Если семейство не настроено, остается владелец, указанный при создании. */
    public void assign(Sku sku) {
        List<User> group = familyOwners(sku.getLine().getFamily());
        if (group.isEmpty()) {
            return;
        }
        Set<User> all = new LinkedHashSet<>(group);
        User primary = primaryFor(sku.getLine());
        sku.getOwners().clear();
        sku.getOwners().addAll(all);
        if (primary != null) {
            sku.setOwner(primary);
        }
    }
}
