# Фаза 2 — Organizations + Multi-tenancy ACL ✅ DONE

> **Статус:** завершена (MVP-достаточно)
> **Даты:** 30 апреля 2026 (старт) — 4 мая 2026 (закрытие)
> **Длительность:** ~7 часов в активные дни (30 апр ~1ч на старте + 3 мая ~3ч + 4 мая ~3ч)

---

## Цели фазы

Реализовать концепцию организации (тенант) с полным CRUD и проверкой прав:
- Любой юзер может создать свою организацию (стать OWNER)
- Один юзер может быть в нескольких организациях с разными ролями
- Только OWNER может менять основные поля своей организации
- Slug организации — глобально уникальный, для красивых URL

---

## Что физически сделано

### Миграция V2

`db/migration/V2__create_organizations_tables.sql`:

```sql
-- Organizations
CREATE TABLE organizations (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL,
    slug        VARCHAR(100)    NOT NULL UNIQUE,
    owner_id    BIGINT          NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_organizations_owner
        FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_organizations_owner ON organizations (owner_id);
CREATE INDEX idx_organizations_slug ON organizations (slug);

-- Memberships (N:N с полями)
CREATE TABLE memberships (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    organization_id BIGINT      NOT NULL,
    role            VARCHAR(32) NOT NULL,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_memberships_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT uk_memberships_user_organization
        UNIQUE (user_id, organization_id)
);

CREATE INDEX idx_memberships_user ON memberships (user_id);
CREATE INDEX idx_memberships_organization ON memberships (organization_id);
```

Заметки:
- `ON DELETE RESTRICT` на owner — не дать удалить юзера, у которого есть организации
- `ON DELETE CASCADE` на membership — при удалении user/org удалять связи
- Composite UNIQUE `(user_id, organization_id)` — один юзер не может быть в одной организации дважды

### Domain код

`organization/`:

- **`Organization.java`** — entity, `@ManyToOne(fetch = LAZY, optional = false)` на User (как `owner`)
- **`Membership.java`** — entity с двумя `@ManyToOne` (user + organization) + `@Enumerated(STRING) role`
- **`OrganizationRole.java`** — enum (OWNER, MANAGER, STAFF)
- **`OrganizationRepository.java`**:
  - `findBySlug(String)`
  - `existsBySlug(String)`
  - `existsBySlugAndIdNot(String slug, Long id)` — для проверки уникальности при апдейте
- **`MembershipRepository.java`**:
  - `findAllByUser(User)` — с **`@Query("SELECT m FROM Membership m JOIN FETCH m.organization WHERE m.user = :user")`**
  - `findMembershipByUserAndOrganization(User, Organization)`
  - `existsByUserAndOrganization(User, Organization)`
- **`SlugAlreadyTakenException.java`** — 409 SLUG_ALREADY_TAKEN, в этом же пакете (domain)

### Универсальные exception в shared

`shared/exception/ForbiddenException.java` — 403 FORBIDDEN, добавлен 4 мая для multi-tenancy ACL.

### DTO

`organization/dto/`:
- **`CreateOrganizationRequest`** — `@NotBlank @Size(2,100) name`, `@NotBlank @Size(3,50) @Pattern("^[a-z0-9]+(-[a-z0-9]+)*$") slug`
- **`UpdateOrganizationRequest`** — поля **опциональны** (PATCH semantics, без `@NotBlank`)
- **`OrganizationResponse`** — record (id, name, slug, ownerId, createdAt). БЕЗ всего `User owner` чтобы не утекали чужие данные

### OrganizationService

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {
    public final MembershipRepository membershipRepository;       // ⚠️ public — должно быть private
    public final OrganizationRepository organizationRepository;   // ⚠️ public — должно быть private

    @Transactional
    public Organization create(String name, String slug, User owner) {
        // 1. Проверка уникальности slug → SlugAlreadyTakenException
        // 2. INSERT Organization
        // 3. INSERT Membership(role=OWNER) для этого юзера
        // 4. Всё в одной транзакции — атомарно
    }

    @Transactional
    public Organization update(Long id, UpdateOrganizationRequest request, User currentUser) {
        // 1. Найти organization по id
        // 2. Найти membership currentUser в этой org
        //    Если нет → ForbiddenException
        //    Если role != OWNER → ForbiddenException
        // 3. Если slug изменился — проверить existsBySlugAndIdNot, поменять
        // 4. Если name прислан — поменять
        // 5. dirty checking — UPDATE без save()
    }

    public Organization findBySlug(String slug) { ... }
    public List<Organization> findMyOrganizations(User user) { ... }
    public OrganizationResponse toResponse(Organization org) { ... }
}
```

### OrganizationController

`/api/v1/organizations`:
- `POST /` (201) — create, возвращает `OrganizationResponse`
- `PATCH /{slug}` — update (только OWNER через ACL в сервисе)
- `GET /me` — мои организации
- `GET /{slug}` — по slug

---

## Архитектурные решения, принятые в этой фазе

### 1. Slug — глобально уникальный, юзер задаёт сам

После долгого обсуждения вариантов (auto-gen с цифрой, id/slug в URL, GitHub-style namespace) — выбрали глобально уникальный slug, заданный юзером.

**Почему:** B2B-платформа, юзер хочет контролировать брендинг URL. Если занят → 409, юзер выбирает другой.

**Что НЕ делаем:**
- Авто-генерация (`coffee-house`, `coffee-house-2`) — выглядит непрофессионально
- GitHub-style `username/orgSlug` — нет username у User, лишняя сложность
- composite UNIQUE — slug глобально уникален

### 2. Многошаговая операция в одной транзакции

`create()` делает **два** INSERT: Organization + Membership(OWNER). Оба в `@Transactional`, чтобы при упавшем втором первый откатился. Без этого можно получить организацию-сироту без owner-membership.

### 3. Денормализация: owner_id + Membership(OWNER)

В `Organization` есть и `@ManyToOne owner`, и в `memberships` создаётся запись с role=OWNER для этого юзера. Зачем оба:
- `owner_id` — быстрый доступ к "главному владельцу" для биллинга
- `Membership(OWNER)` — единообразная модель прав (все права через memberships)

### 4. Multi-tenancy ACL — ручные проверки в сервисе

Не используем `@PreAuthorize` SpEL пока. В каждом write-методе сервиса:

```java
Membership m = membershipRepo
    .findMembershipByUserAndOrganization(currentUser, org)
    .orElseThrow(() -> new ForbiddenException("Not a member"));

if (m.getRole() != OrganizationRole.OWNER) {
    throw new ForbiddenException("Only OWNER can update");
}
```

Когда таких проверок будет 5-10 — переделаем на SpEL.

### 5. PATCH semantics

`UpdateOrganizationRequest` — все поля опциональны (без `@NotBlank`). В сервисе:
```java
if (request.field() != null) { entity.setField(request.field()); }
```

Bean Validation `@Size`, `@Pattern` срабатывают только если поле не null — то что нужно для PATCH.

### 6. `JOIN FETCH` для решения N+1 + LazyInit

`MembershipRepository.findAllByUser` использует `@Query` с `JOIN FETCH m.organization`, чтобы organization загружалась **в той же** транзакции/SQL-запросе. Без этого был бы N+1 + `LazyInitializationException` после возврата из сервиса.

### 7. `ForbiddenException` лучше `AccessDeniedException`

Spring Security иногда конвертирует встроенный `AccessDeniedException` в 401 (а не 403). Свой `BusinessException` идёт через `GlobalExceptionHandler` → возвращает 403 предсказуемо.

---

## Что выучили в этой фазе

### Новые концепции (10 шт за 30 апр)
1. **JPA `@ManyToOne`** — со стороны "много" в N:1 связи
2. **`fetch = FetchType.LAZY`** обязательно на `@ManyToOne` (дефолт EAGER — плохо)
3. **N+1 problem** — концептуально
4. **N:N через промежуточную сущность** (Membership) — когда у связи свои поля
5. **`@JoinColumn(name = ...)`** — явное имя FK-колонки
6. **Идиома `@Transactional(readOnly = true)` на классе** + override для write
7. **Slug как URL-friendly идентификатор**
8. **Денормализация** — намеренное дублирование для удобства запросов
9. **Squash migrations vs immutable** — почему лучше второе
10. **Версионирование API через явные пути**

### Новые концепции (10 шт за 3 мая)
11. **`@Pattern` для regex-валидации DTO** + кастомное message
12. **Атомарность транзакций** в action-методах сервиса
13. **`@AuthenticationPrincipal`** — текущий юзер в контроллере
14. **`@ResponseStatus(CREATED)`** для POST
15. **Method reference `service::toResponse`** в Stream API
16. **`LazyInitializationException`** — стек, причина, решение
17. **`JOIN FETCH` в JPQL** — решение N+1 + lazy init
18. **`@Query` + `@Param`** для кастомных JPQL
19. **404 → /error → 401** — узнаваемый симптом
20. **Чтение stack trace** — как находить точное место

### Новые концепции (5 шт за 4 мая)
21. **Dirty checking** Hibernate — UPDATE без `save()` внутри `@Transactional`
22. **PATCH semantics** — поля опциональны, проверки `if (field != null)`
23. **`existsBy...AndIdNot`** — derived queries с оператором Not
24. **Authentication vs Authorization** — 401 vs 403
25. **Multi-tenancy ACL** — ручные проверки прав в сервисе

---

## Грабли, через которые прошли

### 1. **LazyInitializationException** (3 мая)

`findMyOrganizations` возвращал список Membership с lazy-loaded organization. Транзакция закрывалась, потом контроллер пытался прочитать `org.getName()` → 💥.

**Решение:** `JOIN FETCH` в @Query. Сразу решило N+1 и LazyInit.

### 2. **404 → /error → 401 (снова, 3 мая)**

Опечатка `@RequestMapping("/api/v1/organization")` (без `s`). Запрос на `/organizations` → 404 → forward на `/error` → /error защищён → 401.

**Симптомы:** 401 при валидном токене, **тишина в логах**, **CORS-заголовки в 401-ответе**.

**Урок:** второй раз встретили этот баг. Теперь точно узнаваемый паттерн.

### 3. **Опечатка `equals` без `!`** (4 мая)

```java
if (request.slug() != null && request.slug().equals(org.getSlug())) {
    // нужно было !equals — отрицание
    // эта ветка срабатывала когда slug НЕ менялся, и пыталась его "поменять" на ту же самую строку
    // когда slug РЕАЛЬНО менялся, ничего не происходило
}
```

Тест прошёл частично — name обновлялся, slug нет. Batyrkhan сам нашёл, написал "кажется я забыл !".

**Урок:** в условиях "новое отличается от старого" быть особенно внимательным со знаком отрицания. Тестировать **каждое поле** PATCH отдельно.

### 4. **AccessDeniedException → 401, не 403** (4 мая)

Сначала использовал встроенный Spring `AccessDeniedException` — он почему-то конвертировался в 401, а не 403.

**Решение:** свой `ForbiddenException extends BusinessException` → через GlobalExceptionHandler гарантированно 403.

### 5. **Дилемма URL для PATCH — id или slug?** (4 мая)

Если PATCH меняет slug, старый URL ломается. Обсудили — выбрали slug в URL для консистентности с GET. После PATCH фронт делает редирект на новый URL.

---

## Тестирование

Curl-сценарии (все прошли):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jwt@test.com","password":"password123"}' \
  | jq -r '.accessToken')

# Создание
curl -i -X POST http://localhost:8080/api/v1/organizations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Coffee House","slug":"coffee-house"}'
# → 201

# Дубликат slug → 409
# Невалидный slug (заглавные) → 400

# Мои организации
curl -i http://localhost:8080/api/v1/organizations/me -H "Authorization: Bearer $TOKEN"
# → 200 + массив

# По slug
curl -i http://localhost:8080/api/v1/organizations/coffee-house -H "Authorization: Bearer $TOKEN"
# → 200

# PATCH (поменять имя)
curl -i -X PATCH http://localhost:8080/api/v1/organizations/coffee-house \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Coffee House Almaty"}'
# → 200, name обновлён

# PATCH (поменять slug)
curl -i -X PATCH http://localhost:8080/api/v1/organizations/coffee-house \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"slug":"coffee-house-almaty"}'
# → 200, slug обновлён, старый URL даёт 404

# ACL: хакер пытается изменить чужую → 403
TOKEN2=$(curl ... hacker@test.com ...)
curl -i -X PATCH http://localhost:8080/api/v1/organizations/coffee-house-almaty \
  -H "Authorization: Bearer $TOKEN2" \
  -d '{"name":"HACKED"}'
# → 403 FORBIDDEN
```

Всё работает.

---

## Артефакты фазы

В коде на конец фазы:

```
backend/src/main/java/com/tablebook/
├── organization/
│   ├── Organization, Membership, OrganizationRole
│   ├── OrganizationRepository, MembershipRepository
│   ├── SlugAlreadyTakenException
│   ├── OrganizationService, OrganizationController
│   └── dto/{Create, Update, Response}
└── shared/exception/ForbiddenException
```

В БД на конец фазы:
- Таблицы `organizations` и `memberships` со всеми FK/UNIQUE/индексами
- Тестовые организации: id=1 `coffee-house-almaty`, id=2 `pizza-place` (обе у user_id=8)
- Тестовые memberships: user=8 OWNER в обеих
- `flyway_schema_history` с V1, V2

---

## Что отложили (тех-долги)

- **Эндпоинт пригласить юзера** в организацию (как MANAGER/STAFF) — когда понадобится команда
- **Эндпоинт изменить роль** участника — то же
- **Эндпоинт удалить участника** — то же
- **Универсальная авторизация через `@PreAuthorize` SpEL** — когда ручных проверок будет 5-10

Эти задачи **не блокируют** дальнейшие фазы, поэтому переходим к Фазе 3.

---

## Замеченные неточности в коде (не критично, можно почистить)

См. `current-code-snapshot.md` → раздел "Замеченные неточности". Кратко:

1. `OrganizationService` — поля `public final` вместо `private final` (инкапсуляция)
2. `SecurityConfig` — лишняя строка `/api/v1/test/**` от удалённого UserTestController
3. `TableBookApplication` — форматирование (`}public` слиплись)
4. `JwtAuthenticationFilter.authenticate()` — неиспользуемый параметр `request`
5. `MembershipRepository` — `@Query` без `@Param("user")` (работает по позиции)

Можно почистить в начале Фазы 3 как warm-up.
