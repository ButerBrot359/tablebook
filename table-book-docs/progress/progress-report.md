# TableBook — Прогресс обучения

> Личный трекер. Обновляется после каждой завершённой фазы.
> Дата последнего обновления: **4 мая 2026** (понедельник, вечер)
> Связано с: [[README]] · [[profile]] · [[architecture]] · [[current-code-snapshot]]

---

## Хронология работы

| Дата           | Часы | Что сделано                                                                                  |
|----------------|------|----------------------------------------------------------------------------------------------|
| 27 апр (пн)    | ~3ч  | Roadmap, план, выбор стека и архитектуры, ER-диаграмма                                      |
| 28 апр (вт)    | ~5ч  | Фаза 0 (см. [[phase-0-setup-done]]); Фаза 1 (старт): User entity, Repository, Service, домен exceptions, BCrypt |
| 29 апр (ср)    | ~8ч  | Фаза 1 (финал): рефакторинг пакетов, Login + JWT, JwtAuthenticationFilter, SecurityConfig, защита /api/v1/me |
| 30 апр (чт)    | ~5ч  | Тех-долг (register, чистка UserService, MeController с DTO); Фаза 2 (старт): миграция V2, Organization+Membership entities. Архитектурные обсуждения |
| 3 мая (вс)     | ~4ч  | Фаза 2 (CRUD готов): SlugAlreadyTakenException, DTO, OrganizationService, OrganizationController. Прошли через LazyInitializationException, применили JOIN FETCH |
| 4 мая (пн)     | ~3ч  | Фаза 2 (закрыта, см. [[phase-2-organizations-done]]): PATCH /organizations/{slug}, dirty checking, Multi-tenancy ACL через ForbiddenException. Подготовка инфраструктуры в волте |

**Общий темп:** 5-6 часов в день в активные сессии.
**Всего:** ~28 часов работы за 6 дней.

---

## Где мы сейчас

**Завершено:** [[phase-0-setup-done|Фаза 0]], [[phase-1-auth-done|Фаза 1]], [[phase-2-organizations-done|Фаза 2]] (MVP-достаточно) ✅
**Следующий шаг:** Фаза 3 — Restaurants + Tables (план в [[phase-3-plan]])

```
[Фаза 0] ████████████ 100%   Setup, Flyway, monorepo
[Фаза 1] ████████████ 100%   Auth + JWT (email/password)
[Фаза 2] ████████████ 100%   Organizations (CRUD + ACL)
[Фаза 3] ░░░░░░░░░░░░   0%   Restaurants + Tables  ← СЛЕДУЮЩАЯ
[Фаза P] ░░░░░░░░░░░░   0%   Phone-auth via Telegram
[Фаза 6] ░░░░░░░░░░░░   0%   Booking
[Фаза 7] ░░░░░░░░░░░░   0%   Notifications
[Фаза 8] ░░░░░░░░░░░░   0%   Payments
[Frontend] ░░░░░░░░░░░░  0%
[Deploy]   ░░░░░░░░░░░░  0%
```

Бэк MVP: ~50% готов.

---

## Что физически сделано (краткая сводка)

Подробная история каждой фазы — в архивных файлах. Здесь только итоги.

### Инфраструктура
- Monorepo (backend + frontend + docker-compose.yml)
- Spring Boot 3.5.14 + Java 21 + Maven
- PostgreSQL 16 в Docker
- Flyway, ddl-auto=validate
- IntelliJ IDEA Database Plugin для просмотра БД
- **MCP filesystem** — доступ Claude к коду проекта в новых чатах

### Auth (Фаза 1) ✅
Полная реализация email/password логина и JWT-токенов. Подробности — [[phase-1-auth-done]].

### Organizations (Фаза 2) ✅
Полный CRUD + multi-tenancy ACL. Подробности — [[phase-2-organizations-done]].

### Обработка ошибок
- Иерархия: `BusinessException` → конкретные классы
- Domain exceptions рядом с доменом, универсальные в `shared/`
- `GlobalExceptionHandler` ловит всё через полиморфизм

Подробнее — [[architecture]] раздел "Обработка ошибок".

---

## Уроки 4 мая (новые концепции)

### Dirty checking — "магия" Hibernate
Внутри `@Transactional` менять поля managed-entity достаточно — Hibernate сам сделает UPDATE в конце транзакции. **save() вызывать не нужно.**

### Authentication vs Authorization
- **401 Unauthorized** — нет токена / битый → `JwtAuthenticationFilter`
- **403 Forbidden** — токен есть, но прав нет → **в сервисе** через `ForbiddenException`

### `ForbiddenException` лучше встроенного `AccessDeniedException`
Spring Security иногда конвертирует встроенный `AccessDeniedException` в 401. Свой `BusinessException` идёт через `GlobalExceptionHandler` → возвращает 403 предсказуемо.

### Опечатка `equals` без `!` стоит дорого
Условие "новый slug отличается от старого" должно быть `!equals()`. Без `!` — обратная логика, баг находится только при тестировании.

### `existsBySlugAndIdNot` — derived query с оператором Not
Для проверки "занят кем-то другим, не нами" при апдейте.

### PATCH semantics
Все поля DTO опциональны (без `@NotBlank`). Bean Validation срабатывает только если поле не null. В сервисе — `if (field != null) { ... }`.

---

## Уроки прошлых дней (повторно зафиксировать)

### LazyInitializationException — самая знаменитая JPA-проблема (3 мая)
**Причина:** `@Transactional` метод закончился → Hibernate-сессия закрыта → попытка lazy-load proxy падает.
**Решение:** `JOIN FETCH` в репозитории. Подробности — [[phase-2-organizations-done]].

### Баг "404 → /error → 401" (29 апр + 3 мая, два раза)
Опечатка в URL контроллера → Spring не находит endpoint → 404 → forward на /error → /error защищён → 401.
**Симптомы:** тишина в логах, CORS-заголовки в 401-ответе. Подробности — [[phase-1-auth-done]].

### IDE кэш
IntelliJ иногда не пересобирает класс. Build → Build Project (Cmd+F9) или перезапуск.

---

## Замеченные неточности в текущем коде (не критично)

См. подробно в [[current-code-snapshot]] — раздел "Замеченные неточности". Кратко:

1. `OrganizationService` — поля `public final` вместо `private final` (инкапсуляция)
2. `SecurityConfig` — лишняя строка `/api/v1/test/**` от удалённого тестового контроллера
3. `TableBookApplication` — форматирование (`}public` слиплись)
4. `JwtAuthenticationFilter.authenticate()` — неиспользуемый параметр `request`
5. `MembershipRepository` — `@Query` без `@Param` (работает, но менее ясно)

Можно почистить между фазами как warm-up в начале Фазы 3.

---

## Концепции, которые усвоили (всего 40 пунктов)

См. подробное обоснование в файлах фаз: [[phase-0-setup-done]], [[phase-1-auth-done]], [[phase-2-organizations-done]].

После Фазы 1 (15):
1. Bean и DI
2. `@Component` vs специализации vs `@Bean`
3. Layered architecture
4. `@Transactional`, readOnly, self-invocation
5. Spring Data derived queries
6. Entity vs DTO
7. Spring Security как цепочка фильтров
8. SecurityContextHolder как ThreadLocal
9. Stateless JWT vs sessions
10. CSRF для REST API
11. BCrypt
12. Иерархия исключений
13. HTTP-семантика
14. Flyway + immutable migrations
15. Multi-кэши (Maven, IDEA)

После 30 апреля (10):
16. JPA `@ManyToOne`
17. `fetch = FetchType.LAZY` обязательно
18. N+1 problem (концептуально)
19. N:N через промежуточную сущность (Membership)
20. `@JoinColumn(name = ...)`
21. Идиома `@Transactional(readOnly = true)` на классе
22. Slug как URL-friendly идентификатор
23. Денормализация (owner_id + Membership)
24. Squash migrations vs immutable
25. Версионирование API через явные пути

После 3 мая (10):
26. `@Pattern` для regex-валидации DTO + кастомное message
27. Атомарность транзакций в action-методах сервиса
28. `@AuthenticationPrincipal` — текущий юзер в контроллере
29. `@ResponseStatus(CREATED)` для POST
30. Method reference `service::toResponse` в Stream
31. `LazyInitializationException` — стек, причина, решение
32. `JOIN FETCH` в JPQL — решение N+1 + lazy init
33. `@Query` + `@Param` для кастомных JPQL
34. 404 → /error → 401 — узнаваемый симптом
35. Чтение stack trace

После 4 мая (5):
36. **Dirty checking** Hibernate — UPDATE без save()
37. **PATCH semantics** — поля опциональны, проверки if-not-null
38. **`existsBy...AndIdNot`** — derived queries с Not
39. **Authentication vs Authorization** — 401 vs 403
40. **Multi-tenancy ACL** — ручные проверки прав в сервисе

---

## Текущие тех-долги

| Что | Где | Когда |
|-----|-----|-------|
| Эндпоинты управления членами организации (invite, change role, remove) | organization/ | Когда понадобится команда |
| Универсальная авторизация через @PreAuthorize SpEL | везде | Когда ручных проверок 5-10 |
| Telegram-bot для phone-auth гостей | новая Фаза P | Перед Фазой 6 |
| SMS как fallback к Telegram | Фаза P | После MVP |
| Миграция V?: phone NULLABLE, email NULLABLE, password_hash NULLABLE, +phone_verified | db/migration/ | Фаза P |
| Эндпоинт "complete profile" (гость → менеджер) | auth/user/ | Фаза P |
| Refresh-токены | auth/security/ | После Фазы 3 |
| Тесты для AuthService и фильтра | src/test/java/... | Фаза тестирования |
| Чистка кода (5 пунктов из [[current-code-snapshot]]) | разные места | Между фазами |

---

## Календарь до конца MVP

| Фаза | Что | План (дни) | Факт |
|------|-----|------------|------|
| 0 | Setup | 1-2 | 1 день (28 апр) ✅ [[phase-0-setup-done]] |
| 1 | Auth (email) | 3-4 | 2 дня (28-29 апр) ✅ [[phase-1-auth-done]] |
| 2 | Organizations | 3-4 | 2.5 дня (30 апр + 3-4 мая) ✅ [[phase-2-organizations-done]] |
| 3 | Restaurants + Tables | 4-5 | следующая ([[phase-3-plan]]) |
| P | Phone-auth (Telegram) | 3-4 | — |
| 6 | Booking | 4-5 | — |
| 7 | Notifications | 3-4 | — |
| 8 | Payments | 4-5 | — |
| F | Frontend | 7-10 | — |
| D | Deploy | 3-4 | — |

**Прогноз до полного MVP:** ~22-28 дней работы. С темпом 5-6ч/день и 5 дней в неделю — **5 недель** от сегодня. То есть **середина июня**.

---

## Тестовые данные в БД (актуально)

В БД сейчас есть:
- **User #8** — `jwt@test.com` / `password123`, роль USER, владелец двух организаций
- **User #9** — `hacker@test.com` / `password123`, для тестов прав
- **User #10** — `newuser2@test.com` (тестовая регистрация)

Организации:
- **id=1** — "Coffee House Almaty Restored" (slug: `coffee-house-almaty`)
- **id=2** — "Pizza Place" (slug: `pizza-place`)

Memberships:
- (user_id=8, org_id=1, role=OWNER)
- (user_id=8, org_id=2, role=OWNER)

В Фазе 3 будем создавать рестораны в этих организациях.

---

## Что важно для следующего захода (новый чат)

**Стандартное начало нового чата:**

1. Скажи: "прочитай файлы в волте `TableBook/` и поехали с Фазы 3"
2. Я прочту: [[profile]], [[architecture]], [[progress-report]], [[phase-3-plan]]
3. При необходимости — прочту актуальный код через MCP filesystem
4. Скажу "готов" и начнём с миграции V3 для restaurants

**Где остановились:** Фаза 2 закрыта, проект работает. Готовы начать Фазу 3.

---

## Полезные команды на каждый день

```bash
# Поднять окружение
docker compose up -d

# Получить токен
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jwt@test.com","password":"password123"}' \
  | jq -r '.accessToken')

# Защищённые запросы
curl -i http://localhost:8080/api/v1/me -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/v1/organizations/me -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/v1/organizations/coffee-house-almaty -H "Authorization: Bearer $TOKEN"

# Создание организации
curl -i -X POST http://localhost:8080/api/v1/organizations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"My Cafe","slug":"my-cafe"}'

# Обновление (PATCH) — только OWNER может
curl -i -X PATCH http://localhost:8080/api/v1/organizations/my-cafe \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"My Cafe Updated"}'

# В БД через psql
docker exec -it tablebook-postgres psql -U tablebook -d tablebook
```
