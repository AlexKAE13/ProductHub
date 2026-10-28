# Product HUB

Внутреннее веб-приложение Eltex: единая база знаний по продуктам (SKU) и база артефактов отдела.

## Функциональность

- Каталог SKU: фильтры, жизненный цикл (ЖЦ), заполненность карточек, несколько владельцев у SKU, шаблоны характеристик.
- Карточка SKU: характеристики, ЖЦ, материалы по связям (документация, сравнения и battle cards, фото и видео), прошивки, кейсы, вопросы и ответы.
- База артефактов: типы, языки (RU, EN, RU_EN), статусы, контур (INTERNAL, EXTERNAL), версии файлов, срок ревью, привязка к SKU и линейкам, теги, массовые действия.
- Прошивки: публикация, release notes, контрольная сумма sha256, текущая прошивка в каталоге.
- Кейсы: тексты, теги, привязка к SKU.
- Вопросы и правки: ответы, FAQ, предложения правок карточек.
- Задачи: канбан, автоматические триггеры.
- Поиск: подсказки от 2 символов, нормализация артикулов, морфология RU и EN, опечатки, учет контура.
- Дашборд: покрытие, устаревание, вопросы, задачи, спрос.
- Импорт: Excel (реестр ЖЦ, каталог сайтов, реестр типов документов) и папки с материалами.
- Справочники и администрирование: пользователи, роли, теги, типы артефактов, переиндексация.
- Комментарии к интерфейсу на страницах и выгрузка в файл.
- Аудит изменений, мягкое удаление.
- Две темы оформления (классическая и современная), выбор в настройках пользователя.

## Роли

| Код | Роль |
|---|---|
| PRODUCT | Продакт-менеджер |
| SERVICE_CENTER | Сервисный центр |
| SALES | Менеджер по продажам |
| RND | R&D, техподдержка |
| HEAD | Руководитель |
| ADMIN | Администратор |
| PARTNER | Партнер (только внешний контур, не реализован) |

Права проверяются в сервисах. Контур INTERNAL и EXTERNAL учитывается при любой выдаче данных. Подробно: `SPEC.md`, раздел 6.

## Стек

- Java 21, Spring Boot 4.1.1, Maven Wrapper
- Spring MVC, Thymeleaf, HTMX, SortableJS (локально в `src/main/resources/static/vendor`)
- Spring Data JPA, Flyway
- H2 (профиль `dev`), PostgreSQL 16 (профиль `prod`)
- Spring Security, AD/LDAP
- Lucene 10.5.2 (поиск)
- Apache POI (импорт Excel)
- Caffeine (кэш справочников)
- Spotless, Testcontainers

## Требования

- JDK 21
- Docker (для тестов на PostgreSQL и для развертывания)

## Запуск

```bash
./mvnw spring-boot:run
```

http://localhost:8080. В профиле `dev` вход через список "Войти как".

## Профили

| Профиль | Назначение |
|---|---|
| `dev` (по умолчанию) | H2 в `./data`, переключатель "Войти как" |
| `lan` | Добавляется к `dev`. Порт 7777, адрес 0.0.0.0, тестовые учетные записи скрыты, импорт папок с диска отключен |
| `perf` | Отдельная база и индекс в `./data-perf`, 300 SKU, 5000 артефактов, 300 прошивок, 100 кейсов |
| `prod` | PostgreSQL, настройки из переменных окружения, вход через AD/LDAP |

```bash
SPRING_PROFILES_ACTIVE=dev,lan ./mvnw spring-boot:run
SPRING_PROFILES_ACTIVE=dev,perf SERVER_PORT=8081 ./mvnw spring-boot:run
java -jar target/product-hub-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

## Конфигурация

Переменные окружения профиля `prod`: `.env.example`.

| Переменная | Значение |
|---|---|
| `PRODUCTHUB_DB_URL`, `PRODUCTHUB_DB_USER`, `PRODUCTHUB_DB_PASSWORD` | Подключение к PostgreSQL |
| `PRODUCTHUB_PORT` | Порт приложения (по умолчанию 8080) |
| `PRODUCTHUB_LDAP_*` | Параметры AD/LDAP |

Сопоставление групп AD ролям: `config/application.yml` (см. `docs/deploy.md`).

## Сборка и тесты

```bash
./mvnw verify
./mvnw spotless:apply
```

`verify` включает проверку форматирования (Spotless). Тесты на PostgreSQL (Testcontainers) требуют Docker, без него они пропускаются.

## Развертывание

```bash
cp .env.example .env
docker compose up -d --build
```

Приложение и PostgreSQL в Docker Compose. Состояние: `curl http://localhost:8080/actuator/health`. Подробно: `docs/deploy.md`.

## Данные

| Путь | Содержимое |
|---|---|
| `./data` | База H2, файлы (`data/files`), индекс поиска (`data/index`) профиля `dev` |
| `./data-perf` | Данные профиля `perf` |
| `seed/` | Исходные данные для импорта, не изменяются |

Сброс локальных данных: остановить приложение и удалить `./data`.

## Структура

Пакет `ru.eltex.producthub`:

`sku`, `tag`, `artifact`, `firmware`, `casestudy`, `qa`, `task`, `search`, `dashboard`, `importer`, `security`, `settings`, `feedback`, `transfer`, `common`, `web`

Внутри модуля: `domain`, `repo`, `service`, `web`. Шаблоны: `src/main/resources/templates/<модуль>/`. Миграции: `src/main/resources/db/migration`. Тексты интерфейса: `messages_ru.properties`.

## Документы

| Файл | Содержимое |
|---|---|
| `SPEC.md` | Техническое задание |
| `STATUS.md` | Состояние разработки, решения, замеры скорости |
| `CLAUDE.md` | Правила разработки |
| `docs/deploy.md` | Развертывание, AD/LDAP, резервное копирование |
| `design/` | Макеты |
