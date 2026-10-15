package ru.eltex.producthub.sku;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.service.CatalogFilter;
import ru.eltex.producthub.sku.service.CatalogService;
import ru.eltex.producthub.sku.service.ModelNormalizer;
import ru.eltex.producthub.sku.service.ReferenceDataService;
import ru.eltex.producthub.sku.service.SkuBlock;
import ru.eltex.producthub.sku.service.SkuForm;
import ru.eltex.producthub.sku.service.SkuService;
import ru.eltex.producthub.tag.domain.Tag;
import ru.eltex.producthub.tag.domain.TagCategory;
import ru.eltex.producthub.tag.service.TagService;

/** Теги SKU: продакты сами создают теги и выдают их товарам. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class SkuTagTest {

    @Autowired
    SkuService service;
    @Autowired
    TagService tags;
    @Autowired
    CatalogService catalog;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SkuRepository skus;
    @Autowired
    ReferenceDataService reference;
    @Autowired
    AuditService audit;
    @Autowired
    SeedImportService importer;
    @Autowired
    MockMvc mvc;

    HubUserDetails product;
    HubUserDetails sales;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
        sales = users.loadUserByUsername("sales");
    }

    private Tag newTag() {
        return tags.create(TagCategory.MARKET, "Рынок-" + UUID.randomUUID().toString().substring(0, 6), null, null, product);
    }

    private void assign(String model, Tag... list) {
        Long id = service.findIdByModel(model);
        SkuForm form = service.formFor(id, product);
        form.getTagIds().clear();
        for (Tag t : list) {
            form.getTagIds().add(t.getId());
        }
        service.update(id, SkuBlock.MAIN, form, product);
    }

    @Test
    void tagsAreAssignedToSkuAndShownInCard() {
        Tag market = newTag();
        assign("WEP-30L", market);
        var card = service.loadCard("WEP-30L", sales);
        assertEquals(1, card.tags().size());
        assertEquals(market.getValue(), card.tags().get(0).getValue());
    }

    @Test
    void catalogFiltersByTag() {
        Tag market = newTag();
        assign("WEP-30L", market);
        assign("WOP-30L");
        var found = catalog.search(new CatalogFilter(null, null, null, null, null, market.getId(), false, true, null,
                "model", "asc", 0), sales).items();
        assertEquals(1, found.size());
        assertEquals("WEP-30L", found.get(0).getModel());
    }

    @Test
    void tagChangesAreWrittenToAudit() {
        Tag a = newTag();
        Tag b = newTag();
        assign("WEP-3L", a);
        assign("WEP-3L", a, b);
        var history = audit.history(SkuService.ENTITY, service.findIdByModel("WEP-3L"), 10);
        assertTrue(history.stream().anyMatch(h -> "tags".equals(h.getField()) && h.getNewValue().contains(b.getValue())));
    }

    @Test
    void salesCannotAssignTags() {
        Tag t = newTag();
        Long id = service.findIdByModel("WEP-3L");
        SkuForm form = service.formFor(id, product);
        form.getTagIds().add(t.getId());
        assertThrows(AccessDeniedException.class, () -> service.update(id, SkuBlock.MAIN, form, sales));
    }

    @Test
    void copyKeepsTags() {
        Tag t = newTag();
        assign("WEP-2L", t);
        Long sourceId = service.findIdByModel("WEP-2L");
        Sku source = skus.findById(sourceId).orElseThrow();
        SkuForm copy = new SkuForm();
        copy.setModel("COPY-" + UUID.randomUUID().toString().substring(0, 6));
        copy.setLineId(source.getLine().getId());
        copy.setCategory(source.getCategory());
        copy.setOwnerId(source.getOwner().getId());
        copy.setLifecycleStatus("DEVELOPMENT");
        copy.setCopyFromId(sourceId);
        Sku created = service.create(copy, product);
        assertEquals(1, service.loadCard(created.getModel(), product).tags().size());
        assertEquals(t.getValue(), service.loadCard(created.getModel(), product).tags().get(0).getValue());
    }

    @Test
    @WithUserDetails("sales")
    void catalogPageOffersTagFilterAndCardShowsTagChip() throws Exception {
        Tag t = newTag();
        assign("WEP-1L", t);
        mvc.perform(get("/catalog")).andExpect(content().string(containsString(t.getValue())));
        mvc.perform(get("/sku/WEP-1L")).andExpect(content().string(containsString("class=\"tag\"")))
                .andExpect(content().string(containsString(t.getValue())));
        mvc.perform(get("/catalog?tag=" + t.getId() + "&includeEol=true")).andExpect(content().string(containsString("WEP-1L")));
    }

    @Test
    @WithUserDetails("product")
    void editFormOffersTagPicker() throws Exception {
        newTag();
        Long id = service.findIdByModel("WEP-1L");
        mvc.perform(get("/skus/" + id + "/edit/main")).andExpect(content().string(containsString("name=\"tagIds\"")));
        assertFalse(reference.owners().isEmpty());
        assertEquals(ModelNormalizer.normalize("WEP-1L"), skus.findById(id).orElseThrow().getModelNormalized());
    }
}
