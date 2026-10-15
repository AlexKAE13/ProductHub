package ru.eltex.producthub.sku;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ru.eltex.producthub.sku.service.ModelNormalizer;

class ModelNormalizerTest {

    @Test
    void differentSpellingsGiveSameKey() {
        assertEquals("WEP550K", ModelNormalizer.normalize("WEP-550K"));
        assertEquals("WEP550K", ModelNormalizer.normalize("wep550k"));
        assertEquals("WEP550K", ModelNormalizer.normalize("WEP 550K"));
        assertEquals("WEP550K", ModelNormalizer.normalize("  wep-550k "));
        assertEquals("WEP550K", ModelNormalizer.normalize("wep_550k"));
    }

    @Test
    void caseOfSuffixDoesNotMatter() {
        assertEquals(ModelNormalizer.normalize("WOP-2ac-LR5 SYNC"), ModelNormalizer.normalize("WOP-2ac-LR5 Sync"));
    }

    @Test
    void slashAndDotArePreserved() {
        assertEquals("PM300T220/12", ModelNormalizer.normalize("PM300T-220/12"));
        assertEquals("ESR1511REV.B", ModelNormalizer.normalize("ESR-1511 rev.B"));
    }

    @Test
    void nullGivesEmpty() {
        assertEquals("", ModelNormalizer.normalize(null));
    }
}
