package ru.eltex.producthub.tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.unique;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.tag.domain.Tag;
import ru.eltex.producthub.tag.domain.TagCategory;
import ru.eltex.producthub.tag.service.TagService;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
class TagServiceTest {

    @Autowired
    TagService tags;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    AuditService audit;

    @Test
    void starterTagsExistInAllNeededCategories() {
        assertTrue(tags.all().stream().anyMatch(t -> t.getCategory() == TagCategory.COMPETITOR && t.getValue().equals("Huawei")));
        assertTrue(tags.all().stream().anyMatch(t -> t.getCategory() == TagCategory.SEGMENT && t.getValue().equals("Indoor")));
        assertTrue(tags.all().stream().anyMatch(t -> t.getCategory() == TagCategory.TOPIC && t.getValue().equals("Wi-Fi 6")));
    }

    @Test
    void aliasesAndSpellingVariantsFindTheTag() {
        assertEquals("Ubiquiti", tags.resolve("UniFi", TagCategory.COMPETITOR).orElseThrow().getValue());
        assertEquals("HPE Aruba", tags.resolve("HPE-Aruba", TagCategory.COMPETITOR).orElseThrow().getValue());
        assertEquals("TP-Link", tags.resolve("tp link", null).orElseThrow().getValue());
        assertEquals("Indoor", tags.resolve("INDOOR", TagCategory.SEGMENT).orElseThrow().getValue());
        assertTrue(tags.resolve("Неизвестный вендор", null).isEmpty());
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void deletingTagUnlinksItFreesTheNameAndIsAuditedAndOnlyForManagers() {
        HubUserDetails product = users.loadUserByUsername("product");
        HubUserDetails sales = users.loadUserByUsername("sales");
        String name = unique("Удаляемый");
        Tag tag = tags.create(TagCategory.TOPIC, name, null, "алиас-" + name, product);
        Long skuId = jdbc.queryForObject("select min(id) from sku", Long.class);
        if (skuId != null) {
            jdbc.update("insert into sku_tag (sku_id, tag_id) values (?, ?)", skuId, tag.getId());
            assertEquals(1, tags.usageByTag().get(tag.getId()).skus());
        }

        assertThrows(AccessDeniedException.class, () -> tags.delete(tag.getId(), sales));
        TagService.Usage usage = tags.delete(tag.getId(), product);

        assertEquals(skuId != null ? 1 : 0, usage.total());
        assertEquals(0, jdbc.queryForObject("select count(*) from sku_tag where tag_id = ?", Long.class, tag.getId()));
        assertTrue(tags.all().stream().noneMatch(t -> t.getId().equals(tag.getId())), "удаленный тег не показывается");
        assertTrue(tags.resolve("алиас-" + name, TagCategory.TOPIC).isEmpty(), "синоним больше не находит тег");
        assertEquals("DELETE", audit.history(TagService.ENTITY, tag.getId(), 5).get(0).getAction().name());
        // имя освободилось: такой же тег можно создать заново
        Tag again = tags.create(TagCategory.TOPIC, name, null, null, product);
        assertEquals(name, again.getValue());
        assertThrows(java.util.NoSuchElementException.class, () -> tags.delete(tag.getId(), product), "повторно удалить нельзя");
    }

    @Test
    void onlyProductAndAdminCreateTags() {
        HubUserDetails product = users.loadUserByUsername("product");
        HubUserDetails admin = users.loadUserByUsername("admin");
        HubUserDetails sales = users.loadUserByUsername("sales");
        HubUserDetails service = users.loadUserByUsername("service");

        String name = unique("Новый");
        Tag created = tags.create(TagCategory.TOPIC, name, null, "синоним-" + name, product);
        assertEquals(name, tags.resolve("синоним-" + name, TagCategory.TOPIC).orElseThrow().getValue());
        assertEquals("CREATE", audit.history(TagService.ENTITY, created.getId(), 5).get(0).getAction().name());

        assertEquals(name + "-а", tags.create(TagCategory.TOPIC, name + "-а", null, null, admin).getValue());
        assertThrows(AccessDeniedException.class, () -> tags.create(TagCategory.TOPIC, unique("X"), null, null, sales));
        assertThrows(AccessDeniedException.class, () -> tags.create(TagCategory.TOPIC, unique("X"), null, null, service));
    }

    @Test
    void duplicateAndEmptyTagsAreRejected() {
        HubUserDetails product = users.loadUserByUsername("product");
        assertTrue(assertThrows(FormValidationException.class,
                () -> tags.create(TagCategory.COMPETITOR, "Huawei", null, null, product)).getErrors().containsKey("value"));
        assertTrue(assertThrows(FormValidationException.class,
                () -> tags.create(TagCategory.COMPETITOR, "  ", null, null, product)).getErrors().containsKey("value"));
    }

    @Test
    void disabledTagIsNotResolved() {
        HubUserDetails product = users.loadUserByUsername("product");
        Tag t = tags.create(TagCategory.TOPIC, unique("Временный"), null, null, product);
        assertTrue(tags.resolve(t.getValue(), null).isPresent());
        tags.setActive(t.getId(), false, product);
        assertFalse(tags.resolve(t.getValue(), null).isPresent());
    }

    @Test
    void caseSpaceAndHyphenVariantsAreDuplicates() {
        HubUserDetails product = users.loadUserByUsername("product");
        String base = unique("Дубль");
        tags.create(TagCategory.TOPIC, base, null, null, product);
        for (String variant : new String[] {base.toUpperCase(), base.toLowerCase(), swapCase(base), base + " ", " " + base,
                base.replace("-", " "), base.replace("-", "_"), base.replace("-", "")}) {
            var e = assertThrows(ru.eltex.producthub.tag.service.TagDuplicateException.class,
                    () -> tags.create(TagCategory.TOPIC, variant, null, null, product), "вариант: " + variant);
            assertEquals(base, e.getExisting().getValue());
        }
    }

    @Test
    void duplicateIsDetectedAcrossCategoriesToo() {
        HubUserDetails product = users.loadUserByUsername("product");
        assertThrows(ru.eltex.producthub.tag.service.TagDuplicateException.class,
                () -> tags.create(TagCategory.TOPIC, "huawei", null, null, product), "Huawei уже есть как конкурент");
    }

    @Test
    void englishNameAndAliasCannotRepeatExistingTag() {
        HubUserDetails product = users.loadUserByUsername("product");
        // UniFi - синоним тега Ubiquiti
        var e = assertThrows(ru.eltex.producthub.tag.service.TagDuplicateException.class,
                () -> tags.create(TagCategory.COMPETITOR, "unifi", null, null, product));
        assertEquals("Ubiquiti", e.getExisting().getValue());
        // новое значение, но синоним уже занят
        assertThrows(ru.eltex.producthub.tag.service.TagDuplicateException.class,
                () -> tags.create(TagCategory.COMPETITOR, unique("Новый"), null, "tp link", product));
        // английское название совпало со значением чужого тега
        assertThrows(ru.eltex.producthub.tag.service.TagDuplicateException.class,
                () -> tags.create(TagCategory.SEGMENT, unique("Склады"), "WAREHOUSE", null, product));
    }

    @Test
    void disabledTagStillBlocksItsName() {
        HubUserDetails product = users.loadUserByUsername("product");
        String name = unique("Отключенный");
        Tag t = tags.create(TagCategory.TOPIC, name, null, null, product);
        tags.setActive(t.getId(), false, product);
        assertThrows(ru.eltex.producthub.tag.service.TagDuplicateException.class,
                () -> tags.create(TagCategory.TOPIC, name.toUpperCase(), null, null, product));
    }

    private static String swapCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return sb.toString();
    }
}
