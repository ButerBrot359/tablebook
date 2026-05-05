# Фаза 0 — Setup ✅ DONE

> **Статус:** завершена
> **Дата:** 28 апреля 2026
> **Длительность:** ~5 часов (1 день)

---

## Цели фазы

Подготовить инфраструктуру для разработки backend-а на Spring Boot:
- Создать структуру проекта (monorepo)
- Поднять PostgreSQL в Docker
- Настроить Flyway для миграций
- Базовая конфигурация Spring Boot

---

## Что физически сделано

### Структура monorepo

```
tablebook/
├── backend/           ← Spring Boot
├── frontend/          ← пустая, для будущего React-приложения
├── docker-compose.yml ← PostgreSQL
├── .gitignore
└── README.md
```

### docker-compose.yml для PostgreSQL

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: tablebook-postgres
    environment:
      POSTGRES_DB: ${DB_NAME:-tablebook}
      POSTGRES_USER: ${DB_USER:-tablebook}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-tablebook}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-tablebook}"]
      interval: 5s
      timeout: 5s
      retries: 5
volumes:
  postgres_data:
```

С env-переменными (с дефолтами для удобства локального запуска) и healthcheck.

### Spring Boot проект

- **Spring Boot 3.5.14**
- **Java 21 LTS**
- **Maven** (`pom.xml` + `mvnw`)
- Зависимости:
  - spring-boot-starter-web
  - spring-boot-starter-data-jpa
  - spring-boot-starter-security
  - spring-boot-starter-validation
  - postgresql (runtime)
  - flyway-core + flyway-database-postgresql
  - jjwt-api / jjwt-impl / jjwt-jackson (0.12.6)
  - lombok

### application.properties

```properties
spring.application.name=tablebook

# Datasource — параметры из env-переменных, дефолты для local dev
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:tablebook}
spring.datasource.username=${DB_USER:tablebook}
spring.datasource.password=${DB_PASSWORD:tablebook}

# JPA: validate (схему контролирует Flyway)
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false
```

### Flyway миграция V1 (users)

`backend/src/main/resources/db/migration/V1__create_users_table.sql`:

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    phone           VARCHAR(50),
    platform_role   VARCHAR(32)  NOT NULL DEFAULT 'USER',
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);
```

### Подключение к БД в IDEA Database Plugin

- Параметры: `localhost:5432`, БД `tablebook`, юзер/пароль `tablebook/tablebook`
- TablePlus оказался платным → используем встроенный плагин IDEA Community

---

## Архитектурные решения, принятые в этой фазе

1. **Java 21 LTS** (НЕ 26) — Spring Boot 3.5 поддерживает 17-24 официально
2. **PostgreSQL 16** в Docker — production-ready БД, проще чем Postgres native
3. **Maven**, не Gradle — стандарт Spring-мира, проще для новичка
4. **Flyway** + `ddl-auto=validate` — production-стандарт для миграций
5. **Monorepo** (backend + frontend в одном репо) — удобно для индивидуального проекта
6. **Env-переменные с дефолтами** — для локального запуска без `.env` файла
7. **`spring.jpa.open-in-view=false`** — отключаем антипаттерн, чтобы видеть N+1 явно

---

## Что выучили в этой фазе

1. Структура Spring Boot проекта (Maven, src/main/java, src/main/resources)
2. Зачем нужны Flyway миграции (vs `ddl-auto=update`)
3. Как настраивается datasource через env-переменные
4. Docker Compose для локальной БД с healthcheck
5. Базовая работа с IntelliJ IDEA Database Plugin

---

## Грабли, на которые наступали

- **`Found more than one migration with version 1`** — старый файл миграции остался в `target/`. Решение: `mvn clean`. Урок: при удалении файлов из `src/` чистить `target/`.
- **TablePlus платный** — переключились на IDEA Database Plugin (бесплатный).

---

## Артефакты фазы

В коде на конец фазы:
- `tablebook/docker-compose.yml`
- `tablebook/backend/pom.xml`
- `tablebook/backend/src/main/resources/application.properties`
- `tablebook/backend/src/main/resources/db/migration/V1__create_users_table.sql`
- Базовый `TableBookApplication.java`

В БД на конец фазы:
- Таблица `users` (пустая)
- Таблица `flyway_schema_history` с записью V1
