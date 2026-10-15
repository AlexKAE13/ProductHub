package ru.eltex.producthub.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ru.eltex.producthub.artifact.repo.ArtifactTypeRepository;
import ru.eltex.producthub.sku.repo.SkuRepository;

/** При первом старте в dev, если база SKU пуста, загружает данные из seed/ (чтобы каталог не был пустым). */
@Component
@Profile("dev")
@Order(1)
public class InitialImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialImportRunner.class);

    private final ImportProperties props;
    private final SkuRepository skus;
    private final SeedImportService importer;
    private final ArtifactTypeRepository types;

    public InitialImportRunner(ImportProperties props, SkuRepository skus, SeedImportService importer,
                                ArtifactTypeRepository types) {
        this.props = props;
        this.skus = skus;
        this.importer = importer;
        this.types = types;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.onFirstStart() && (skus.count() == 0 || types.count() == 0)) {
            log.info("Справочники пусты, запускаю импорт из {}", props.seedDir());
            importer.execute("system");
        }
    }
}
