package ru.eltex.producthub.settings;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;

/** Современное оформление: личный выбор вида, схемы, фона и обоев. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class ModernLookTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserSettingsService service;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    SeedImportService importer;
    @Autowired
    WallpaperService wallpapers;

    HubUserDetails sales;
    HubUserDetails product;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        sales = users.loadUserByUsername("sales");
        product = users.loadUserByUsername("product");
        service.save(sales.getId(), UserSettingsView.DEFAULTS);
        service.save(product.getId(), UserSettingsView.DEFAULTS);
    }

    static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    @WithUserDetails("sales")
    void classicIsDefaultAndLoadsNoModernStyles() throws Exception {
        mvc.perform(get("/catalog")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-ui=\"classic\"")))
                .andExpect(content().string(not(containsString("modern.css"))))
                .andExpect(content().string(not(containsString("id=\"bg\""))));
    }

    @Test
    @WithUserDetails("sales")
    void modernLookIsSavedPerUserAndAppliedToPages() throws Exception {
        mvc.perform(post("/settings").with(csrf()).param("menuLayout", "SIDE").param("density", "NORMAL")
                        .param("startPage", "HOME").param("uiTheme", "MODERN").param("colorMode", "DARK")
                        .param("palette", "LILAC").param("background", "DOTS").param("wallpaper", "peaks")
                        .param("wallBlur", "10").param("wallDim", "30"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/catalog"))
                .andExpect(content().string(containsString("data-ui=\"modern\"")))
                .andExpect(content().string(containsString("data-theme=\"dark\"")))
                .andExpect(content().string(containsString("data-palette=\"lilac\"")))
                .andExpect(content().string(containsString("data-bgtype=\"dots\"")))
                .andExpect(content().string(containsString("css/modern")))
                .andExpect(content().string(containsString("theme-modern")));
        assertEquals(UiTheme.CLASSIC, service.get(product.getId()).look().theme(), "чужой вид не меняется");
    }

    @Test
    void forgedLookValuesAreNormalized() {
        Look bad = new Look(null, null, null, null, "../../etc/passwd", null, 999, -5).sanitized();
        assertEquals("dunes", bad.wallpaper());
        assertEquals(40, bad.blur());
        assertEquals(0, bad.dim());
        assertEquals(UiTheme.CLASSIC, bad.theme());
        Look custom = new Look(UiTheme.MODERN, ColorMode.AUTO, Palette.ELTEX, Background.PHOTO, "custom", null, 18, 0).sanitized();
        assertEquals("dunes", custom.wallpaper(), "custom без загруженного файла недопустим");
    }

    @Test
    @WithUserDetails("sales")
    void wallpaperIsResizedStoredAndShownOnlyToOwner() throws Exception {
        MockMultipartFile big = new MockMultipartFile("file", "w.png", "image/png", png(3000, 1500));
        mvc.perform(multipart("/settings/wallpaper").file(big).with(csrf())).andExpect(status().is3xxRedirection());
        Look look = service.get(sales.getId()).look();
        assertEquals("custom", look.wallpaper());
        assertEquals(Background.PHOTO, look.background());
        assertNotNull(look.wallpaperKey());
        byte[] body = mvc.perform(get("/settings/wallpaper")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(body));
        assertEquals(1920, img.getWidth(), "уменьшено до 1920 px");
        assertEquals(960, img.getHeight());
        assertEquals(null, service.get(product.getId()).look().wallpaperKey(), "у другого пользователя обоев нет");

        mvc.perform(post("/settings/wallpaper/delete").with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals("dunes", service.get(sales.getId()).look().wallpaper());
        mvc.perform(get("/settings/wallpaper")).andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("sales")
    void notAnImageIsRejected() throws Exception {
        MockMultipartFile junk = new MockMultipartFile("file", "w.png", "image/png", "not an image".getBytes());
        mvc.perform(multipart("/settings/wallpaper").file(junk).with(csrf()))
                .andExpect(flash().attribute("wallError", "settings.wallpaper.bad"));
        assertTrue(service.get(sales.getId()).look().wallpaperKey() == null);
    }

    @Test
    @WithUserDetails("sales")
    void tooBigFileIsRejected() throws Exception {
        MockMultipartFile huge = new MockMultipartFile("file", "w.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        mvc.perform(multipart("/settings/wallpaper").file(huge).with(csrf()))
                .andExpect(flash().attribute("wallError", "settings.wallpaper.tooBig"));
    }

    @Test
    @WithUserDetails("sales")
    void themeToggleInMenuSavesLightOrDarkOnly() throws Exception {
        mvc.perform(post("/settings").with(csrf()).param("menuLayout", "SIDE").param("uiTheme", "MODERN").param("palette", "PINE")
                .param("background", "DOTS").param("colorMode", "LIGHT")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/catalog")).andExpect(content().string(containsString("id=\"themeToggle\"")));
        mvc.perform(post("/settings/mode").with(csrf()).param("mode", "DARK")).andExpect(status().isNoContent());
        Look look = service.get(sales.getId()).look();
        assertEquals(ColorMode.DARK, look.mode());
        assertEquals(Palette.PINE, look.palette(), "схема и фон не меняются");
        assertEquals(Background.DOTS, look.background());
        mvc.perform(get("/catalog")).andExpect(content().string(containsString("data-theme=\"dark\"")));
    }

    @Test
    @WithUserDetails("sales")
    void classicHasNoThemeToggle() throws Exception {
        mvc.perform(get("/catalog")).andExpect(content().string(not(containsString("themeToggle"))));
    }

    @Test
    @WithUserDetails("sales")
    void settingsPageShowsLookSection() throws Exception {
        mvc.perform(get("/settings")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Оформление")))
                .andExpect(content().string(containsString("Современное")))
                .andExpect(content().string(containsString("Цветовая схема")));
    }
}
