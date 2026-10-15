package ru.eltex.producthub.sku;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.dashboard.service.DashboardService;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.service.CatalogFilter;
import ru.eltex.producthub.sku.service.CatalogService;
import ru.eltex.producthub.sku.service.SkuBlock;
import ru.eltex.producthub.sku.service.SkuForm;
import ru.eltex.producthub.sku.service.SkuPermissions;
import ru.eltex.producthub.sku.service.SkuService;

/** Совладельцы SKU: распределение по семействам, права, фильтр каталога, цифры дашборда, правка и повторный импорт. */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class SkuOwnersTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SeedImportService importer;
    @Autowired
    SkuService service;
    @Autowired
    SkuRepository skus;
    @Autowired
    SkuPermissions permissions;
    @Autowired
    CatalogService catalog;
    @Autowired
    DashboardService dashboard;
    @Autowired
    AuditService audit;
    @Autowired
    HubUserDetailsService users;

    HubUserDetails product;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
    }

    private Set<String> owners(String model) {
        return Set.copyOf(jdbc.queryForList("select u.login from sku_owner so join sku s on s.id = so.sku_id"
                + " join app_user u on u.id = so.user_id where s.model = ?", String.class, model));
    }

    private String primary(String model) {
        return jdbc.queryForObject("select u.login from sku s join app_user u on u.id = s.owner_id where s.model = ?", String.class, model);
    }

    private String anyModelOfFamily(String family) {
        return jdbc.queryForList("select s.model from sku s join product_line l on l.id = s.line_id where l.family = ? and s.deleted = false"
                + " order by s.model", String.class, family).get(0);
    }

    // ----------------------------------------------------------- распределение по семействам

    @Test
    void importGivesEveryFamilyItsCoOwners() {
        assertEquals(Set.of("kuznetsov", "smith"), owners("WEP-550K"));
        assertEquals(Set.of("polukhin", "litosh"), owners("LTP-16N"));
        assertEquals(Set.of("polukhin", "litosh", "maslov"), owners(anyModelOfFamily("MES")));
        assertEquals(Set.of("polukhin", "litosh", "maslov"), owners(anyModelOfFamily("ESR")));
        assertEquals(Set.of("osokin", "nagornova"), owners(anyModelOfFamily("Media")));
        assertEquals(Set.of("golovanov"), owners(anyModelOfFamily("VoIP")));
    }

    @Test
    void primaryOwnerIsAlwaysAmongOwnersAndRotatesAcrossLines() {
        for (String model : List.of("WEP-550K", "WOP-30L", "LTP-16N", anyModelOfFamily("MES"))) {
            assertTrue(owners(model).contains(primary(model)), model + ": основной среди владельцев");
        }
        assertEquals("kuznetsov", primary("WEP-550K"), "первая линейка семейства достается первому в списке");
        assertEquals("smith", primary("WOP-30L"), "вторая линейка - второму");
        assertEquals(0, jdbc.queryForObject("select count(*) from sku s where not exists"
                + " (select 1 from sku_owner so where so.sku_id = s.id and so.user_id = s.owner_id)", Integer.class),
                "у всех SKU основной владелец есть в списке владельцев");
    }

    @Test
    void reimportDoesNotResetOwners() {
        Long id = service.findIdByModel("WEP-30L");
        jdbc.update("update sku set owner_id = (select id from app_user where login = 'smith') where id = ?", id);
        importer.execute("test");
        assertEquals("smith", primary("WEP-30L"), "повторный импорт не возвращает владельца из реестра");
    }

    // ----------------------------------------------------------- права, каталог, дашборд

    @Test
    void coOwnerIsNotForeignButOutsiderIs() {
        var sku = skus.findCardById(service.findIdByModel("WEP-550K")).orElseThrow();
        HubUserDetails kuznetsov = users.loadUserByUsername("kuznetsov");
        HubUserDetails smith = users.loadUserByUsername("smith");
        HubUserDetails polukhin = users.loadUserByUsername("polukhin");
        assertFalse(permissions.isForeign(kuznetsov, sku));
        assertFalse(permissions.isForeign(smith, sku), "совладелец правит без пометки \"чужой SKU\"");
        assertTrue(permissions.isForeign(polukhin, sku));
    }

    @Test
    void catalogOwnerFilterFindsSkuForEveryCoOwner() {
        for (String login : List.of("kuznetsov", "smith")) {
            Long id = users.loadUserByUsername(login).getId();
            var found = catalog.search(new CatalogFilter("Wi-Fi", null, null, null, id, null, false, true, null, "model", "asc", 0), product);
            var sku = found.items().stream().filter(s -> s.getModel().equals("WEP-550K")).findFirst().orElseThrow();
            assertTrue(sku.getOwnerNames().contains("Кузнецов") && sku.getOwnerNames().contains("Смит"), "в каталоге видны все владельцы: " + sku.getOwnerNames());
        }
        Long polukhin = users.loadUserByUsername("polukhin").getId();
        var none = catalog.search(new CatalogFilter("Wi-Fi", null, null, null, polukhin, null, false, true, null, "model", "asc", 0), product);
        assertTrue(none.items().stream().noneMatch(s -> s.getModel().equals("WEP-550K")));
    }

    @Test
    void dashboardCountsSkuForEveryCoOwner() {
        for (String login : List.of("kuznetsov", "smith", "golovanov", "polukhin")) {
            Long id = users.loadUserByUsername(login).getId();
            long expected = jdbc.queryForObject("select count(*) from sku s where s.deleted = false and exists"
                    + " (select 1 from sku_owner so where so.sku_id = s.id and so.user_id = ?)", Long.class, id);
            assertTrue(expected > 0, login);
            assertEquals(expected, dashboard.build(id).skuTotal(), login + ": цифра дашборда = число SKU, где он владелец или совладелец");
        }
        Long smith = users.loadUserByUsername("smith").getId();
        Long kuznetsov = users.loadUserByUsername("kuznetsov").getId();
        assertTrue(dashboard.build(smith).skuTotal() > 0 && dashboard.build(kuznetsov).skuTotal() > 0);
    }

    // ----------------------------------------------------------- правка

    @Test
    void editingOwnersIsSavedAndAudited() {
        Long id = service.findIdByModel("WLC-3200");
        SkuForm form = service.formFor(id, product);
        Long polukhin = users.loadUserByUsername("polukhin").getId();
        form.getOwnerIds().add(polukhin);
        service.update(id, SkuBlock.MAIN, form, product);
        assertTrue(owners("WLC-3200").contains("polukhin"));
        var change = audit.history(SkuService.ENTITY, id, 20).stream().filter(h -> "owners".equals(h.getField())).findFirst().orElseThrow();
        assertTrue(change.getNewValue().contains("polukhin"));

        SkuForm back = service.formFor(id, product);
        back.getOwnerIds().remove(polukhin);
        service.update(id, SkuBlock.MAIN, back, product);
        assertFalse(owners("WLC-3200").contains("polukhin"));
    }

    @Test
    void primaryOwnerCannotBeDroppedFromOwners() {
        Long id = service.findIdByModel("WLC-3200");
        SkuForm form = service.formFor(id, product);
        form.getOwnerIds().clear();
        service.update(id, SkuBlock.MAIN, form, product);
        assertTrue(owners("WLC-3200").contains(primary("WLC-3200")));
    }
}
