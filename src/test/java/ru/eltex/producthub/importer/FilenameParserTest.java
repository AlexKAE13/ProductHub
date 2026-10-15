package ru.eltex.producthub.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.artifact.domain.Language;

/** Правила разбора из application.yml проверяются на именах файлов из реальной рабочей папки Battle Cards. */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class FilenameParserTest {

    @Autowired
    FilenameParser parser;

    @Test
    void comparisonGivesCompetitorAndSegment() {
        var parsed = parser.parse("Eltex_vs_Huawei_Indoor_EN.pdf");
        assertEquals(Language.EN, parsed.language());
        assertEquals("Eltex_vs_Huawei_Indoor", parsed.body());
        var match = parser.match(parsed.body());
        assertNotNull(match);
        assertEquals("Сравнение с конкурентами", match.type());
        assertEquals("Huawei", match.groups().get("competitor"));
        assertEquals("Indoor", match.groups().get("segment"));
    }

    @Test
    void namesWithSpacesAndLowercaseWork() {
        var parsed = parser.parse("Eltex vs Ruckus indoor_EN.pdf");
        var match = parser.match(parsed.body());
        assertEquals("Ruckus", match.groups().get("competitor"));
        assertEquals("indoor", match.groups().get("segment"));
        assertEquals("HPE-Aruba", parser.match(parser.parse("Eltex_vs_HPE-Aruba_Indoor_RU.pptx").body()).groups().get("competitor"));
        assertEquals("TP-Link", parser.match(parser.parse("Eltex vs TP-Link Indoor_RU.pdf").body()).groups().get("competitor"));
    }

    @Test
    void pptxAndPdfWithSameNameAreOneGroup() {
        assertEquals(parser.parse("Eltex vs UniFi Indoor_EN.pptx").key(), parser.parse("Eltex vs UniFi Indoor_EN.pdf").key());
        assertEquals(parser.parse("WEP-3ax_RU.pptx").key(), parser.parse("WEP-3ax_RU.pdf").key());
    }

    @Test
    void trailingVersionNumberDoesNotBreakPairing() {
        // в папке: Eltex_AP_Guide_EN_1.pptx и Eltex_AP_Guide_EN.pdf
        assertEquals(parser.parse("Eltex_AP_Guide_EN.pdf").key(), parser.parse("Eltex_AP_Guide_EN_1.pptx").key());
        assertEquals(Language.EN, parser.parse("Eltex_AP_Guide_EN_1.pptx").language());
    }

    @Test
    void guideGetsGuideType() {
        assertEquals("Гайд по подбору оборудования", parser.match(parser.parse("Eltex_AP_Guide_RU.pdf").body()).type());
    }

    @Test
    void productCardNamesHaveNoRuleAndLanguageIsDetected() {
        for (String name : new String[] {"WEP-30L_EN.pdf", "WB-3P-LR5_RU.pptx", "WLC_EN.pdf", "vWLC_RU.pdf"}) {
            assertNull(parser.match(parser.parse(name).body()), name);
        }
        assertEquals(Language.RU, parser.parse("БШПД-оборудование_RU.pdf").language());
        assertEquals("БШПД-оборудование", parser.parse("БШПД-оборудование_RU.pdf").body());
        assertEquals(Language.RU, parser.parse("WB-3P-LR5_RU.pptx").language());
        assertEquals("WB-3P-LR5", parser.parse("WB-3P-LR5_RU.pptx").body());
    }

    @Test
    void broadbandWirelessMaterialsGetTopicTag() {
        var match = parser.match(parser.parse("FBWA_EN.pptx").body());
        assertEquals("Карточка продукта", match.type());
        assertEquals("FBWA", match.groups().get("topic"));
        assertEquals("БШПД", parser.match(parser.parse("БШПД-оборудование_RU.pdf").body()).groups().get("topic"));
    }

    @Test
    void sourcesFileIsInternalOther() {
        var match = parser.match(parser.parse("WEP-550K_источники.md").body());
        assertNotNull(match);
        assertEquals("Прочее", match.type());
        assertEquals("INTERNAL", match.contour());
    }

    @Test
    void serviceFilesAreIgnored() {
        assertTrue(parser.isIgnored("~$WB-3P-LR5_EN.pptx"));
        assertTrue(parser.isIgnored(".DS_Store"));
        assertTrue(parser.isIgnored("RU.zip"));
        assertFalse(parser.isIgnored("WEP-550K_RU.pdf"));
    }

    @Test
    void fileWithoutLanguageSuffixHasNoLanguage() {
        assertEquals(Language.NONE, parser.parse("Прайс 2026.xlsx").language());
    }

    @Test
    void bothLanguagesSuffix() {
        assertEquals(Language.RU_EN, parser.parse("Datasheet_RU_EN.pdf").language());
    }
}
