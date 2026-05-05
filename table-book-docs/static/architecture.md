# TableBook — Архитектурные решения (LOCKED)

> Долгоживущие решения проекта. Меняются редко, осознанно. Читать в начале каждого нового чата.
> Связано с: [[README]] · [[profile]] · [[progress-report]] · [[spring-cheatsheet]]

---

## Что это за проект

**TableBook** — SaaS-платформа бронирования столиков в ресторанах. Аналог OpenTable / Resy для рынка Казахстана.

**Бизнес-модель:**
- Владельцы ресторанов регистрируются, создают свою "организацию" (сеть)
- В организации могут быть несколько ресторанов (физических точек)
- В ресторане — несколько столов
- Гости заходят на публичную страницу ресторана и бронируют столик

**Целевая аудитория:**
- B2B сторона — рестораторы (Казахстан, средний+премиум сегмент)
- B2C сторона — гости (через публичные URL ресторанов)

---

## Технологический стек (LOCKED)

| Слой | Решение |
|------|---------|
| Язык | Java 21 LTS |
| Фреймворк | Spring Boot 3.5.14 |
| БД | PostgreSQL 16 |
| ORM | JPA + Hibernate 6 |
| Миграции | Flyway (`ddl-auto=validate`) |
| Билд | Maven |
| Auth | JWT (HS512, 15 мин TTL), refresh-токены отложены |
| Контейнеры | Docker Compose для локальной БД |
| Frontend | React + Vite + TS + TanStack Query (после бэка) |

**Java 21 (НЕ 26)** — потому что Spring Boot 3.5 официально поддерживает 17-24, не 26. (Этот выбор обоснован в [[phase-1-auth-done]] раздел "Грабли".)

---

## Структурные решения

### Пакеты — by feature

```
com.tablebook/
├── auth/                  ← всё про аутентификацию (см. [[phase-1-auth-done]])
│   ├── login/             ← AuthController, AuthService, login DTO
│   ├── security/          ← JWT, фильтры, конфиги
│   └── user/              ← User entity, UserService, MeController
│       └── dto/
├── organization/          ← Organization, Membership, OrganizationService (см. [[phase-2-organizations-done]])
│   └── dto/
├── (будущее: restaurant/, booking/, payment/, ...)
└── shared/                ← общая инфраструктура
    └── exception/         ← BusinessException + handler + общие 404/403
```

**Domain exceptions — рядом с доменом:**
- `EmailAlreadyInUseException` → `auth/user/`
- `SlugAlreadyTakenException` → `organization/`
- `InvalidCredentialsException` → `auth/login/`

**Универсальные — в `shared/`:**
- `BusinessException` (абстрактная база)
- `ResourceNotFoundException` (универсальный 404)
- `ForbiddenException` (универсальный 403)

### IDs

- **Long** для большинства entity (User, Organization, Membership, Restaurant, Table)
- **UUID для Booking** — публичные ссылки на бронь не должны быть угадываемыми (`/booking/abc-uuid`)
- **Slug** для красивых URL (Organization, Restaurant)

### Время

- **`OffsetDateTime` + `TIMESTAMPTZ`** везде, не `LocalDateTime`
- Часовой пояс хранится с временем
- Это важно для бронирований через API из разных tz

### API версионирование

- Пути пишутся **явно**: `@RequestMapping("/api/v1/...")` в каждом контроллере
- Никаких констант или `application.properties` для префикса
- При v2 → создаются **новые** контроллеры в `v2/`, старые **остаются** для backward compat

---

## Multi-tenancy решения (КЛЮЧЕВОЕ)

См. подробности реализации в [[phase-2-organizations-done]].

### Модель тенантов
- **Organization** = тенант
- **Restaurant** принадлежит Organization (FK) — Фаза 3, см. [[phase-3-plan]]
- **Table** принадлежит Restaurant (FK) — Фаза 3
- **Booking** будет привязан к Restaurant (через который и тенант определяется) — Фаза 6

### Связь User ↔ Organization

**Не `@ManyToMany`!** Используем промежуточную сущность **`Membership`** с полями:
- `user_id` (FK)
- `organization_id` (FK)
- `role` (`OrganizationRole` enum: OWNER, MANAGER, STAFF)
- `joinedAt`
- timestamps

**Причина:** N:N связь со своими полями (роль) → отдельная entity, всегда. Это правило "связь без полей → @ManyToMany; связь с полями → отдельная entity с двумя @ManyToOne".

### Денормализация: Organization.owner_id + Membership(role=OWNER)

В `Organization` есть и `@ManyToOne owner`, и для этого же юзера создаётся `Membership(role=OWNER)`.

**Зачем оба:**
- `owner_id` — быстрый доступ к "главному владельцу" для биллинга и админских операций
- `Membership(OWNER)` — для единообразной модели прав (все права через memberships)

При создании организации **в одной транзакции** создаются оба объекта.

### Авторизация (ACL)

**Текущий подход:** ручные проверки в сервисе.

```java
Membership m = membershipRepo.findMembershipByUserAndOrganization(currentUser, org)
    .orElseThrow(() -> new ForbiddenException("Not a member"));

if (m.getRole() != OrganizationRole.OWNER) {
    throw new ForbiddenException("Only OWNER can update organization");
}
```

**На будущее:** когда таких проверок станет 5-10, перейдём на `@PreAuthorize` SpEL с кастомным bean типа:
```java
@PreAuthorize("@orgSecurity.canManage(#orgId, authentication.principal)")
```

Пока это overkill, ручные проверки нагляднее.

---

## Slug и URL стратегия

### Organization slug

- **Глобально уникальный** (не per-owner, не per-region)
- **Юзер задаёт сам** при создании, может поменять в настройках
- Если занят → `409 SLUG_ALREADY_TAKEN`
- Формат: `^[a-z0-9]+(-[a-z0-9]+)*$`, длина 3-50
- В БД: `UNIQUE` constraint на `organizations.slug`

URL: `/api/v1/organizations/{slug}` — например `/coffee-house-empire`

### Restaurant slug (Фаза 3, см. [[phase-3-plan]])

- **Уникален в рамках организации** (composite UNIQUE `(organization_id, slug)`)
- Юзер задаёт сам
- URL: `/api/v1/organizations/{orgSlug}/restaurants/{restSlug}` — например `/coffee-house-empire/coffee-house-almaty`

Это GitHub-стиль: `github.com/{user}/{repo}`. Гостевой URL читаемый.

### Booking — UUID, не slug

- `/booking/{uuid}` — броня не должна быть угадываемой
- Slug бессмысленен (никто не запоминает)

---

## Аутентификация и юзеры

См. реализацию в [[phase-1-auth-done]].

### Текущее состояние (Фаза 1)
- Email + password регистрация / логин
- JWT access-токен в заголовке `Authorization: Bearer ...`
- BCrypt для хеширования паролей
- TTL access-токена: 15 минут (короткий, безопасно)
- **Refresh-токены отложены** — добавим после Фазы 3 или при необходимости

### Фаза P (отложена) — Phone-auth для гостей

**Решение для MVP:** через **Telegram-бот** (не SMS). Имя бота: `@TableBookKzBot` (зарезервировано).

**Почему Telegram:**
- Бесплатно (SMS стоят денег ~3-5 KZT/штука через Mobizon)
- Простая интеграция (Telegram bot API)
- В Казахстане Telegram распространён в целевой аудитории

**После MVP:** SMS как fallback для тех, у кого нет TG.

### Модель юзеров — единая таблица users

**НЕ разделяем** на отдельные таблицы guests/owners. Одна таблица `users`, разная "наполненность":

| Поле | Гость (lite) | Менеджер/Owner |
|------|--------------|----------------|
| `email` | NULL | обязательно |
| `password_hash` | NULL (логин по TG) | обязательно |
| `phone` | обязательно, verified | опционально |
| `phone_verified` | true | true/false |
| `email_verified` | false | false → true |
| `full_name` | обязательно | обязательно |

**Это потребует миграцию V?** (когда будем делать Фазу P):
- `email` → NULLABLE
- `password_hash` → NULLABLE
- `phone` → UNIQUE (NULL допустим, но если есть — уникален)
- Добавить `phone_verified BOOLEAN NOT NULL DEFAULT FALSE`

**Гость может стать менеджером** через "complete profile" эндпоинт (добавить email + password).

### Один человек = один юзер
Если менеджер `bob@cafe.com` потом бронит как гость по своему телефону — это **тот же** юзер. Никаких дубликатов. При попытке гостевой регистрации с phone, который уже есть у юзера → логиним существующего.

---

## Обработка ошибок

### Иерархия

```
BusinessException (abstract)
├── EmailAlreadyInUseException (409, auth/user/)
├── InvalidCredentialsException (401, auth/login/)
├── SlugAlreadyTakenException (409, organization/)
├── ResourceNotFoundException (404, shared/) — универсальный
└── ForbiddenException (403, shared/) — универсальный
```

### GlobalExceptionHandler

Один `@RestControllerAdvice` ловит:
- `BusinessException` → возвращает соответствующий статус и code
- `MethodArgumentNotValidException` → 400 VALIDATION_FAILED с описанием полей

**Формат ответа:**
```json
{
  "timestamp": "2026-05-04T12:34:56+05:00",
  "status": 409,
  "code": "SLUG_ALREADY_TAKEN",
  "message": "Slug already taken: coffee-house"
}
```

### HTTP коды

| Код | Когда используем |
|-----|-------------------|
| 200 | Успешный GET, PATCH |
| 201 | Успешный POST (через `@ResponseStatus(HttpStatus.CREATED)`) |
| 204 | Успешный DELETE |
| 400 | VALIDATION_FAILED, невалидный запрос |
| 401 | UNAUTHENTICATED — нет JWT, битый, невалидные credentials |
| 403 | FORBIDDEN — есть JWT, но нет прав |
| 404 | RESOURCE_NOT_FOUND |
| 409 | EMAIL_ALREADY_IN_USE, SLUG_ALREADY_TAKEN |
| 500 | реальный баг сервера |

**401 vs 403:**
- 401 — кто ты вообще?
- 403 — окей, ты Bob, но это не твоё

---

## Транзакции и JPA

См. шпаргалку по конкретным паттернам в [[spring-cheatsheet]].

### Идиома `@Transactional` в сервисе

```java
@Service
@Transactional(readOnly = true)   // дефолт для read-методов
public class MyService {

    public Entity find(...) { ... }      // readOnly от класса

    @Transactional                        // override для write
    public Entity create(...) { ... }
}
```

### Связи `@ManyToOne` — ВСЕГДА LAZY

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "..._id", nullable = false)
private OtherEntity field;
```

**Дефолт `@ManyToOne` — EAGER. Это плохо.** Всегда явно ставить LAZY.

### N+1 problem и LazyInitializationException

**Симптомы:**
- Список запросов в логах вместо одного
- Или: `LazyInitializationException` после возврата из `@Transactional` метода

**Решение:** `JOIN FETCH` в репозитории.

```java
@Query("SELECT m FROM Membership m JOIN FETCH m.organization WHERE m.user = :user")
List<Membership> findAllByUser(User user);
```

История бага у нас: см. [[phase-2-organizations-done]] раздел "Грабли".

### Dirty checking

Внутри `@Transactional` менять поля managed-entity достаточно — Hibernate сам сделает UPDATE при коммите. **save() вызывать не нужно.**

```java
@Transactional
public Entity update(Long id, ...) {
    Entity e = repo.findById(id)...;
    e.setField(newValue);   // Hibernate отследит и сделает UPDATE
    return e;               // НЕТ save()!
}
```

---

## Миграции (Flyway)

### Правила
- ✅ Имена: `V<N>__<описание>.sql` (два подчёркивания!)
- ✅ Применённую миграцию **никогда не редактировать** — только новая миграция
- ✅ `ddl-auto=validate` — Hibernate только проверяет
- ✅ Все изменения схемы через миграции, не через TablePlus/IDE

### Существующие миграции

| Версия | Что |
|--------|-----|
| V1 | `users` table (см. [[phase-0-setup-done]]) |
| V2 | `organizations` + `memberships` tables (см. [[phase-2-organizations-done]]) |

### Запланированные

| Версия | Что | Когда |
|--------|-----|-------|
| V3 | `restaurants` table | Фаза 3, см. [[phase-3-plan]] |
| V4 | `tables` table | Фаза 3 |
| V?? | users: phone NULL/UNIQUE/verified, email/password NULLABLE | Фаза P |
| V?? | `bookings` table | Фаза 6 |
| V?? | `payments` table | Фаза 8 |

---

## DTO правила

### Request DTOs — `record` с валидацией

```java
public record CreateRequest(
    @NotBlank @Size(min = 2, max = 100) String name,
    @NotBlank @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
             message = "must contain only lowercase letters, digits, and hyphens")
    String slug
) {}
```

### Response DTOs — `record` без валидации

```java
public record ResponseDto(
    Long id,
    String name,
    OffsetDateTime createdAt
) {}
```

**НЕВЕРНО** возвращать entity напрямую — могут утекать поля типа `passwordHash`. **Всегда** через `toResponse()` метод сервиса.

### PATCH — поля опциональны

Без `@NotBlank`. `@Size`, `@Pattern` срабатывают только если поле не null.

```java
public record UpdateRequest(
    @Size(min = 2, max = 100) String name,    // нет @NotBlank
    @Size(min = 3, max = 50) @Pattern(...) String slug
) {}
```

В сервисе: `if (request.field() != null) { ... }` — паттерн PATCH semantics.

---

## Что отложено (тех-долги)

| Что | Когда |
|-----|-------|
| Telegram-bot для phone-auth (Фаза P) | Перед Фазой 6 |
| SMS как fallback к Telegram | После MVP |
| Refresh-токены | После Фазы 3 |
| Эндпоинты управления членами организации (invite/change role/remove) | По мере необходимости |
| Универсальная авторизация через `@PreAuthorize` SpEL | Когда ручных проверок 5-10 |
| Тесты | Фаза тестирования (после основного функционала) |
| Audit-поля (createdBy, updatedBy) | Когда понадобится |

Подробный список замеченных мелких неточностей в коде — см. [[current-code-snapshot]].

---

## Ключевые соглашения

1. **Никогда** не клади пароль/passwordHash в Response DTO
2. **Всегда** валидируй DTO через `@Valid` + bean validation
3. **Всегда** `OffsetDateTime`, не `LocalDateTime`
4. **Никогда** не редактируй применённую Flyway миграцию
5. **Никогда** `EnumType.ORDINAL` — всегда `EnumType.STRING`
6. **Всегда** возвращай `Optional<T>` из репозитория для "может не найти"
7. **Entity = mutable**, **DTO = record (immutable)**
8. **Stateless API** = `csrf.disable()` + `STATELESS` session policy
9. **`@ManyToOne` всегда LAZY** + `JOIN FETCH` для list-кейсов
10. **PATCH без `@NotBlank`**, проверки `if (field != null)`
11. **Dirty checking** — UPDATE без `save()` внутри `@Transactional`
12. **403 через `ForbiddenException`**, не `AccessDeniedException` Spring-а
13. **Domain exceptions** живут рядом с доменом, в `shared/` только инфраструктура
14. **Многошаговые операции** — в одной `@Transactional` (атомарность)
