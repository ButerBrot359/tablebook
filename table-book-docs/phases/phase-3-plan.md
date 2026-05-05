# Фаза 3 — Restaurants + Tables (план)

> Зафиксировано 4 мая 2026. Стартуем завтра.

---

## Архитектурные решения (зафиксировано)

### 1. URL — гибрид (вариант C, GitHub-style)

```
POST   /api/v1/organizations/{orgSlug}/restaurants                — создать ресторан
GET    /api/v1/organizations/{orgSlug}/restaurants                — список ресторанов организации (публично)
GET    /api/v1/organizations/{orgSlug}/restaurants/{restSlug}     — конкретный ресторан (публично)
PATCH  /api/v1/organizations/{orgSlug}/restaurants/{restSlug}     — обновить (OWNER+MANAGER)
DELETE /api/v1/organizations/{orgSlug}/restaurants/{restSlug}     — удалить (только OWNER)
```

Гостевой URL читаемый: `tablebook.com/coffee-house-empire/coffee-house-almaty` — сразу понятно "чей и какой ресторан".

### 2. Slug ресторана уникален в рамках организации

В БД: `UNIQUE (organization_id, slug)` — composite unique constraint.

Логика:
- В организации "Coffee House Empire" только один ресторан с slug `coffee-house-almaty`
- В другой организации может быть свой ресторан с тем же `coffee-house-almaty` — не конфликтует

При создании проверяем `existsByOrganizationAndSlug(org, slug)`.

### 3. Доступ к эндпоинтам

| Действие | Кто может |
|----------|-----------|
| GET список ресторанов организации | Все (публично, без аутентификации) |
| GET один ресторан по slug | Все (публично) |
| POST создать ресторан | OWNER + MANAGER организации |
| PATCH обновить | OWNER + MANAGER организации |
| DELETE удалить | Только OWNER |

**Публичный доступ через SecurityConfig** (Вариант А):

```java
.requestMatchers(HttpMethod.GET, "/api/v1/organizations/*/restaurants/**").permitAll()
```

Все GET-ы на этом пути публичны. Это для "витрины" — гости смотрят рестораны и потом бронируют.

**Авторизация для write-операций** через ручные проверки в сервисе (как в OrganizationService.update):
```java
Membership m = membershipRepository.findMembershipByUserAndOrganization(currentUser, org)
    .orElseThrow(() -> new ForbiddenException("Not a member"));

if (m.getRole() != OrganizationRole.OWNER && m.getRole() != OrganizationRole.MANAGER) {
    throw new ForbiddenException("Only OWNER or MANAGER can create restaurants");
}
```

Для DELETE — только `OrganizationRole.OWNER`.

### 4. Адрес — простые поля в Restaurant

Не отдельная entity, не `@Embedded`. Просто поля:

```java
private String address;       // улица, дом
private String city;          // Алматы, Астана
private String country;       // KZ
private Double latitude;      // опционально
private Double longitude;     // опционально
```

Если потом понадобится — отрефакторим на `@Embedded Address`.

### 5. Что **отложили** (не делаем в Фазе 3)

- Фотографии ресторанов
- Расписание работы (часы открытия, выходные)
- Категории кухни / типа заведения
- Поиск ресторанов по городу/координатам

---

## Структура данных

### Таблица `restaurants`

```sql
CREATE TABLE restaurants (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    description     TEXT,
    address         VARCHAR(500) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    country         VARCHAR(2)   NOT NULL,
    latitude        DECIMAL(10,8),
    longitude       DECIMAL(11,8),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_restaurants_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,

    CONSTRAINT uk_restaurants_organization_slug
        UNIQUE (organization_id, slug)
);

CREATE INDEX idx_restaurants_organization ON restaurants (organization_id);
CREATE INDEX idx_restaurants_city ON restaurants (city);
```

**Заметки:**
- `ON DELETE CASCADE` — если организация удаляется, её рестораны тоже (логично)
- Composite UNIQUE `(organization_id, slug)` — slug уникален внутри организации
- Индекс на `city` — для будущего поиска по городу
- `country` — 2 символа (ISO код типа "KZ", "RU")
- `description` — TEXT (без лимита, может быть длинное описание)

### Таблица `tables` (отдельная сущность ресторана)

```sql
CREATE TABLE tables (
    id            BIGSERIAL    PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL,
    label         VARCHAR(50)  NOT NULL,        -- "Стол №5", "Терраса 1", "VIP-кабинет"
    capacity      INT          NOT NULL,         -- сколько человек помещается
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_tables_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,

    CONSTRAINT chk_tables_capacity_positive
        CHECK (capacity > 0 AND capacity <= 50)
);

CREATE INDEX idx_tables_restaurant ON tables (restaurant_id);
```

**Заметки:**
- `label` вместо `number` — потому что не всегда тупо "1, 2, 3" (бывает "Терраса", "VIP")
- `CHECK` constraint — защита от некорректных значений (без негативных, без 100500)
- `ON DELETE CASCADE` — удаляем ресторан → удаляем все его столы

---

## Что новое в технологиях (предстоит выучить)

1. **`@PathVariable` с двумя параметрами** — `{orgSlug}` и `{restSlug}` в одном URL
2. **`requestMatchers(HttpMethod.GET, ...)` permitAll** — публичные эндпоинты
3. **CHECK constraint** в миграции — для валидации на уровне БД
4. **Двухуровневая иерархия ресурсов** в URL и коде
5. **DECIMAL для координат** — точность для geographic data
6. **ACL "OWNER + MANAGER"** — расширение существующих проверок

---

## Порядок шагов (как будем делать)

1. **Миграция V3** — таблица `restaurants` (одной миграцией, или вместе с tables — обсудим)
2. **Entity Restaurant** + связь `@ManyToOne` на Organization
3. **RestaurantRepository** с derived queries
4. **DTO**: CreateRestaurantRequest, UpdateRestaurantRequest, RestaurantResponse
5. **RestaurantService** — create, find, update, delete с ACL
6. **RestaurantController** — все 5 эндпоинтов
7. **SecurityConfig** — добавить permitAll для GET-эндпоинтов
8. **Тесты через curl** для всех сценариев

После Restaurant — переходим к Table:
9. Миграция (или сразу в V3)
10. Entity Table
11. RestaurantTableRepository
12. Эндпоинты для управления столами

---

## Открытые вопросы (решим завтра)

- Делать одну миграцию V3 для restaurants+tables или две (V3 для restaurants, V4 для tables)?
- Отдавать ли в RestaurantResponse список столов сразу или отдельным запросом?
- Нужен ли DTO для Table или достаточно полей в Restaurant с tables-массивом?

---

## Текущее состояние проекта (на 4 мая, конец дня)

Что работает:
- POST/GET/PATCH организаций с правильными правами
- Один OWNER (`jwt@test.com`, id=8) с двумя организациями: `coffee-house-almaty` и `pizza-place`
- Один лишний юзер `hacker@test.com` для тестов прав

Можно завтра использовать:
- Токен для `jwt@test.com` / password123
- Организации уже есть, в них можно создавать рестораны
