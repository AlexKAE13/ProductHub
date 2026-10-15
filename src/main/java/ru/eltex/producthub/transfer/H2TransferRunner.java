package ru.eltex.producthub.transfer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Разовый перенос данных из файла H2 (producthub.transfer.h2-file, путь без расширения .mv.db) в базу приложения и выход.
 * Запуск описан в docs/deploy.md (раздел "Перенос данных из пилота").
 */
@Component
@ConditionalOnProperty("producthub.transfer.h2-file")
public class H2TransferRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(H2TransferRunner.class);

    private final DataSource target;
    private final ConfigurableApplicationContext context;
    private final String file;

    public H2TransferRunner(DataSource target, ConfigurableApplicationContext context,
                            @org.springframework.beans.factory.annotation.Value("${producthub.transfer.h2-file}") String file) {
        this.target = target;
        this.context = context;
        this.file = file;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String url = "jdbc:h2:file:" + file + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE;ACCESS_MODE_DATA=r";
        try (Connection source = DriverManager.getConnection(url, "sa", ""); Connection dst = target.getConnection()) {
            Map<String, Integer> counts = new H2DataTransfer().copy(source, dst);
            counts.forEach((table, rows) -> log.info("Перенос: {} - {} строк", table, rows));
            log.info("Перенос завершен: {} таблиц. Файлы (data/files) нужно скопировать отдельно, индекс поиска построится при следующем запуске.",
                    counts.size());
        } catch (Exception e) {
            log.error("Перенос не удался, база не изменена: {}", e.getMessage(), e);
            System.exit(SpringApplication.exit(context, () -> 1));
        }
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
