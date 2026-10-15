package ru.eltex.producthub.importer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import ru.eltex.producthub.artifact.domain.ArtifactType;
import ru.eltex.producthub.artifact.domain.CardBlock;
import ru.eltex.producthub.artifact.repo.ArtifactTypeRepository;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.sku.domain.Contour;

/** Типы артефактов из seed/document_types_registry.xlsx (SPEC.md 4.4, 5.11 п. 3). Существующие типы не меняются. */
@Component
public class ArtifactTypeSeedImporter {

    static final String FILE = "document_types_registry.xlsx";
    static final String SHEET = "Реестр по типам";

    private final ArtifactTypeRepository types;
    private final ArtifactTypeService typeService;
    private final ImportProperties props;

    public ArtifactTypeSeedImporter(ArtifactTypeRepository types, ArtifactTypeService typeService,
                                    ImportProperties props) {
        this.types = types;
        this.typeService = typeService;
        this.props = props;
    }

    /** @return число созданных типов, строки журнала добавляются в log */
    public int importTypes(Path dir, List<String> log) throws IOException {
        ExcelTable table = ExcelTable.read(dir.resolve(FILE), SHEET, "Тип документа", "Важность (1-5)");
        int created = 0;
        int order = (int) types.count();
        for (int i = 0; i < table.size(); i++) {
            String name = table.text(i, "Тип документа");
            if (name == null) {
                continue;
            }
            name = name.trim();
            if (props.skipTypes().contains(name)) {
                log.add("Типы: \"" + name + "\" пропущен (это данные SKU, а не материал)");
                continue;
            }
            if (types.findByName(name).isPresent()) {
                continue;
            }
            int importance = parseInt(table.text(i, "Важность (1-5)"), 3);
            types.save(new ArtifactType(name, importance, contour(table.text(i, "Внеш / Внутр")),
                    table.text(i, "Производитель"), table.text(i, "Потребитель"), 90, cardBlock(name), ++order * 10));
            created++;
        }
        for (String extra : props.extraTypes()) {
            if (types.findByName(extra).isEmpty()) {
                types.save(new ArtifactType(extra, 3, Contour.INTERNAL, null, null, 90, cardBlock(extra), ++order * 10));
                created++;
            }
        }
        typeService.evict();
        return created;
    }

    /** "Оба" считается внутренним: внешний контур продакт включает вручную (решение этапа 2). */
    private static Contour contour(String text) {
        return text != null && text.trim().equalsIgnoreCase("Внешний") ? Contour.EXTERNAL : Contour.INTERNAL;
    }

    private CardBlock cardBlock(String name) {
        String value = props.find(props.cardBlockByType(), name);
        return value == null ? CardBlock.DOCS : CardBlock.valueOf(value);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
