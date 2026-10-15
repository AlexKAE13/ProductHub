package ru.eltex.producthub.firmware;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.firmware.domain.Firmware;
import ru.eltex.producthub.firmware.domain.FirmwareStatus;
import ru.eltex.producthub.firmware.event.FirmwarePublished;
import ru.eltex.producthub.firmware.service.FirmwareFilter;
import ru.eltex.producthub.firmware.service.FirmwareForm;
import ru.eltex.producthub.firmware.service.FirmwareService;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.service.SkuService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@RecordApplicationEvents
class FirmwareTest {

    @Autowired
    FirmwareService service;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SeedImportService importer;
    @Autowired
    SkuService skuService;
    @Autowired
    MockMvc mvc;
    @Autowired
    ApplicationEvents events;
    @Autowired
    EntityManagerFactory emf;

    HubUserDetails product;
    HubUserDetails rnd;
    HubUserDetails serviceCenter;
    HubUserDetails sales;
    HubUserDetails partner;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
        rnd = users.loadUserByUsername("rnd");
        serviceCenter = users.loadUserByUsername("service");
        sales = users.loadUserByUsername("sales");
        partner = users.loadUserByUsername("partner");
    }

    private static String version() {
        return "9." + UUID.randomUUID().toString().substring(0, 6);
    }

    private FirmwareForm form(String version, String skus, FirmwareStatus status, LocalDate date) {
        FirmwareForm f = new FirmwareForm();
        f.setVersion(version);
        f.setSkus(skus);
        f.setStatus(status.name());
        f.setReleaseDate(date.toString());
        f.setReleaseNotes("Исправления " + version);
        return f;
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream",
                ("firmware " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    }

    private Firmware publish(String version, String skus, FirmwareStatus status, LocalDate date) {
        return service.create(form(version, skus, status, date), file("fw-" + version + ".bin"), product).firmware();
    }

    // ----------------------------------------------------------- критерий этапа: три SKU видят прошивку как текущую

    @Test
    void firmwareWithThreeSkusIsCurrentInAllThreeCards() {
        String v = version();
        publish(v, "WEP-30L, WOP-30L, WLC-3200", FirmwareStatus.RELEASE, LocalDate.now());
        for (String model : List.of("WEP-30L", "WOP-30L", "WLC-3200")) {
            Long id = skuService.findIdByModel(model);
            var fw = service.forSku(id, product);
            assertNotNull(fw.current(), model);
            assertEquals(v, fw.current().getVersion(), model);
        }
        assertNull(service.forSku(skuService.findIdByModel("WEP-550K"), product).current(), "у SKU без прошивки текущей нет");
    }

    @Test
    void currentIsLatestReleaseByDateAndOthersGoBelow() {
        Long id = skuService.findIdByModel("WEP-3L");
        String older = version();
        String newer = version();
        String beta = version();
        publish(older, "WEP-3L", FirmwareStatus.RELEASE, LocalDate.now().minusDays(30));
        publish(newer, "WEP-3L", FirmwareStatus.RELEASE, LocalDate.now().minusDays(1));
        publish(beta, "WEP-3L", FirmwareStatus.BETA, LocalDate.now());
        var fw = service.forSku(id, product);
        assertEquals(newer, fw.current().getVersion(), "бета новее, но текущей считается последняя RELEASE");
        assertTrue(fw.others().stream().anyMatch(f -> f.getVersion().equals(older)));
        assertTrue(fw.others().stream().anyMatch(f -> f.getVersion().equals(beta)));
        assertFalse(fw.others().stream().anyMatch(f -> f.getVersion().equals(newer)));
    }

    @Test
    void withdrawnFirmwareIsNeverCurrent() {
        Long id = skuService.findIdByModel("WEP-2L");
        String good = version();
        String bad = version();
        publish(good, "WEP-2L", FirmwareStatus.RELEASE, LocalDate.now().minusDays(10));
        Firmware broken = publish(bad, "WEP-2L", FirmwareStatus.RELEASE, LocalDate.now());
        assertEquals(bad, service.forSku(id, product).current().getVersion());
        service.changeStatus(broken.getId(), FirmwareStatus.WITHDRAWN, product);
        assertEquals(good, service.forSku(id, product).current().getVersion(), "после отзыва текущей становится прежняя");
    }

    // ----------------------------------------------------------- событие FirmwarePublished

    @Test
    void eventIsPublishedOnlyForRelease() {
        long before = events.stream(FirmwarePublished.class).count();
        publish(version(), "WEP-1L", FirmwareStatus.BETA, LocalDate.now());
        assertEquals(before, events.stream(FirmwarePublished.class).count(), "бета событие не создает");

        String v = version();
        publish(v, "WEP-1L, WEP-2ac", FirmwareStatus.RELEASE, LocalDate.now());
        var published = events.stream(FirmwarePublished.class).toList();
        assertEquals(before + 1, published.size());
        FirmwarePublished e = published.get(published.size() - 1);
        assertEquals(v, e.version());
        assertEquals(2, e.skuIds().size());
        assertEquals("product", e.userLogin());
    }

    @Test
    void betaBecomingReleaseTriggersEvent() {
        Firmware f = publish(version(), "WEP-1L", FirmwareStatus.BETA, LocalDate.now());
        long before = events.stream(FirmwarePublished.class).count();
        service.changeStatus(f.getId(), FirmwareStatus.RELEASE, product);
        assertEquals(before + 1, events.stream(FirmwarePublished.class).count());
        service.changeStatus(f.getId(), FirmwareStatus.RELEASE, product);
        assertEquals(before + 1, events.stream(FirmwarePublished.class).count(), "повторная установка того же статуса не шумит");
    }

    // ----------------------------------------------------------- права

    @Test
    void productServiceCenterRndAndAdminPublishOthersCannot() {
        service.create(form(version(), "WEP-30L", FirmwareStatus.BETA, LocalDate.now()), file("a.bin"), rnd);
        service.create(form(version(), "WEP-30L", FirmwareStatus.BETA, LocalDate.now()), file("b.bin"), serviceCenter);
        assertThrows(AccessDeniedException.class,
                () -> service.create(form(version(), "WEP-30L", FirmwareStatus.BETA, LocalDate.now()), file("c.bin"), sales));
        Firmware f = publish(version(), "WEP-30L", FirmwareStatus.BETA, LocalDate.now());
        assertThrows(AccessDeniedException.class, () -> service.changeStatus(f.getId(), FirmwareStatus.RELEASE, sales));
        assertThrows(AccessDeniedException.class, () -> service.delete(f.getId(), sales));
    }

    @Test
    void partnerSeesOnlyPublicFirmware() {
        Firmware hidden = publish(version(), "WEP-30L", FirmwareStatus.RELEASE, LocalDate.now());
        FirmwareForm pub = form(version(), "WEP-30L", FirmwareStatus.RELEASE, LocalDate.now());
        pub.setPublicRelease(true);
        Firmware open = service.create(pub, file("p.bin"), product).firmware();
        assertThrows(AccessDeniedException.class, () -> service.detail(hidden.getId(), partner));
        assertThrows(AccessDeniedException.class, () -> service.fileForDownload(hidden.getId(), partner));
        assertNotNull(service.detail(open.getId(), partner));
        Long skuId = skuService.findIdByModel("WEP-30L");
        assertEquals(open.getId(), service.forSku(skuId, partner).current().getId());
        assertTrue(service.search(new FirmwareFilter(null, null, null, null, 0), partner).items().stream()
                .allMatch(Firmware::isPublicRelease));
    }

    // ----------------------------------------------------------- проверка данных

    @Test
    void anyFileFormatIsAccepted() {
        for (String name : new String[] {"fw.bin", "fw.img", "fw.tar.gz", "fw.ipk", "fw.firmware", "fw"}) {
            Firmware f = service.create(form(version(), "WEP-30L", FirmwareStatus.BETA, LocalDate.now()), file(name), product).firmware();
            assertEquals(name, f.getFileName());
            assertEquals(64, f.getSha256().length());
        }
    }

    @Test
    void requiredFieldsAndUnknownSkuAreRejected() {
        FirmwareForm empty = new FirmwareForm();
        var errors = assertThrows(FormValidationException.class, () -> service.create(empty, null, product)).getErrors();
        assertTrue(errors.keySet().containsAll(List.of("version", "skus", "file")), errors.toString());

        FirmwareForm bad = form(version(), "НЕТ-ТАКОГО", FirmwareStatus.RELEASE, LocalDate.now());
        assertTrue(assertThrows(FormValidationException.class, () -> service.create(bad, file("x.bin"), product))
                .getErrors().containsKey("skus"));
    }

    @Test
    void linkInsteadOfFileIsAllowed() {
        FirmwareForm f = form(version(), "WEP-30L", FirmwareStatus.RELEASE, LocalDate.now());
        f.setExternalUrl("https://example.test/fw.bin");
        Firmware created = service.create(f, null, product).firmware();
        assertFalse(created.hasFile());
        assertEquals("https://example.test/fw.bin", created.getExternalUrl());
    }

    @Test
    void sameVersionOnSameSkuGivesWarningButIsSaved() {
        String v = version();
        publish(v, "WEP-30L", FirmwareStatus.BETA, LocalDate.now());
        var second = service.create(form(v, "WEP-30L", FirmwareStatus.BETA, LocalDate.now()), file("again.bin"), product);
        assertFalse(second.warnings().isEmpty());
        assertTrue(second.warnings().get(0).contains("WEP-30L"));
    }

    @Test
    void changesAreWrittenToAudit() {
        Firmware f = publish(version(), "WEP-30L", FirmwareStatus.BETA, LocalDate.now());
        FirmwareForm form = service.formFor(f.getId(), product);
        form.setStatus("RELEASE");
        form.setReleaseNotes("Новые заметки");
        service.update(f.getId(), form, null, product);
        var history = service.detail(f.getId(), product).history();
        assertTrue(history.stream().anyMatch(h -> "status".equals(h.getField()) && "RELEASE".equals(h.getNewValue())
                && h.getAction().name().equals("STATUS_CHANGE")));
        assertTrue(history.stream().anyMatch(h -> "releaseNotes".equals(h.getField())));
        assertTrue(history.stream().anyMatch(h -> h.getAction().name().equals("CREATE")));
    }

    @Test
    void softDeleteHidesFirmware() {
        Firmware f = publish(version(), "WEP-30L", FirmwareStatus.RELEASE, LocalDate.now());
        service.delete(f.getId(), product);
        assertThrows(RuntimeException.class, () -> service.detail(f.getId(), product));
    }

    @Test
    void firmwareIsNotPartOfCompleteness() {
        Long id = skuService.findIdByModel("WEP-30L");
        int before = skuService.loadCard(id, product).sku().getCompleteness();
        publish(version(), "WEP-30L", FirmwareStatus.RELEASE, LocalDate.now());
        skuService.refreshCompleteness(id);
        assertEquals(before, skuService.loadCard(id, product).sku().getCompleteness());
    }

    // ----------------------------------------------------------- веб

    @Test
    @WithUserDetails("sales")
    void downloadHasEtagRangeAndAttachment() throws Exception {
        Firmware f = publish(version(), "WEP-30L", FirmwareStatus.RELEASE, LocalDate.now());
        mvc.perform(get("/firmware/" + f.getId() + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + f.getSha256() + "\""))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/firmware/" + f.getId() + "/download").header("Range", "bytes=0-7"))
                .andExpect(status().isPartialContent());
        mvc.perform(get("/firmware/" + f.getId() + "/download").header("If-None-Match", "\"" + f.getSha256() + "\""))
                .andExpect(status().isNotModified());
    }

    @Test
    @WithUserDetails("sales")
    void listAndDetailPagesWork() throws Exception {
        String v = version();
        Firmware f = publish(v, "WEP-30L", FirmwareStatus.RELEASE, LocalDate.now());
        mvc.perform(get("/firmware?q=" + v)).andExpect(status().isOk()).andExpect(content().string(containsString(v)));
        mvc.perform(get("/firmware?q=" + v + "&status=BETA").header("HX-Request", "true"))
                .andExpect(content().string(not(containsString(v))));
        mvc.perform(get("/firmware/" + f.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Исправления " + v)));
        mvc.perform(get("/firmware/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("product")
    void publishFormThroughHttp() throws Exception {
        String v = version();
        var file = new MockMultipartFile("file", "wep.bin", "application/octet-stream", "data".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/firmware").file(file).with(csrf()).param("version", v).param("releaseDate", LocalDate.now().toString())
                        .param("status", "RELEASE").param("skus", "WEP-30L, WOP-30L"))
                .andExpect(status().is3xxRedirection());
        var current = service.forSku(skuService.findIdByModel("WOP-30L"), product).current();
        assertEquals(v, current.getVersion());
    }

    @Test
    @WithUserDetails("sales")
    void skuCardShowsCurrentFirmwareBlockAndCatalogColumn() throws Exception {
        String v = version();
        publish(v, "WEP-30L", FirmwareStatus.RELEASE, LocalDate.now());
        mvc.perform(get("/sku/WEP-30L")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Прошивки")))
                .andExpect(content().string(containsString(v)))
                .andExpect(content().string(containsString("Release notes")));
        mvc.perform(get("/catalog?q=WEP-30L&includeEol=true")).andExpect(content().string(containsString(v)));
    }

    @Test
    void cardWithFirmwareStaysWithinQueryBudget() {
        publish(version(), "WEP-30L", FirmwareStatus.RELEASE, LocalDate.now());
        Long id = skuService.findIdByModel("WEP-30L");
        service.forSku(id, product);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        service.forSku(id, product);
        assertTrue(stats.getPrepareStatementCount() <= 1, "запросов на блок прошивок: " + stats.getPrepareStatementCount());
    }
}
