# Фаза 1 — Auth (email/password + JWT) ✅ DONE

> **Статус:** завершена
> **Даты:** 28 апреля 2026 (старт) — 30 апреля 2026 (тех-долг)
> **Длительность:** ~17 часов (28 апр ~5ч + 29 апр ~8ч + 30 апр ~4ч на тех-долг)

---

## Цели фазы

Полноценная аутентификация пользователей через email/password с JWT-токенами:
- Регистрация юзера
- Логин с возвратом JWT
- Защищённые эндпоинты по токену
- Custom 401 ответы в нашем формате
- Эндпоинт `/api/v1/me` для получения текущего юзера

---

## Что физически сделано

### Иерархия исключений (заложена основа всего проекта)

`shared/exception/`:
- **`BusinessException`** — абстрактная база с `getStatus()` и `getCode()`
- **`ResourceNotFoundException`** — универсальный 404
- **`GlobalExceptionHandler`** — `@RestControllerAdvice`, ловит все `BusinessException` + `MethodArgumentNotValidException`

Формат ответа `ErrorResponse`:
```json
{
  "timestamp": "...",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "User not found 42"
}
```

### User domain

`auth/user/`:
- **`User.java`** — entity с полями id, email, passwordHash, fullName, phone, platformRole, emailVerified, createdAt, updatedAt
- **`PlatformRole.java`** — enum (USER, PLATFORM_ADMIN)
- **`UserRepository.java`** — JpaRepository с derived queries `findByEmail`, `existsByEmail`
- **`UserService.java`** — `findUserById`, `checkPassword`, `toResponse`. На классе `@Transactional(readOnly = true)`
- **`MeController.java`** — `GET /api/v1/me`, возвращает `UserResponse` через `@AuthenticationPrincipal`
- **`EmailAlreadyInUseException.java`** — 409, в этом же пакете (domain exception)
- **`dto/UserResponse.java`** — record (id, email, fullName, phone, platformRole, emailVerified, createdAt) — БЕЗ passwordHash!

### Auth/Login

`auth/login/`:
- **`AuthController.java`** — `POST /api/v1/auth/login` и `POST /api/v1/auth/register` (201 + автологин)
- **`AuthService.java`** — `login` (read-only), `register` (`@Transactional`)
- **`InvalidCredentialsException.java`** — 401
- **`dto/LoginRequest.java`** — `@Email @NotBlank email`, `@NotBlank password`
- **`dto/LoginResponse.java`** — `accessToken`, `tokenType`. Factory method `bearer(token)`
- **`dto/RegisterRequest.java`** — `@Email @NotBlank email`, `@NotBlank @Size(min=8) password`, `@NotBlank fullName`, опциональный `phone`

### Security

`auth/security/`:
- **`SecurityConfig.java`** — stateless, csrf disabled, custom entry point, `/api/v1/auth/**` permitAll, остальное authenticated, JwtAuthenticationFilter перед UsernamePasswordAuthenticationFilter
- **`JwtService.java`** — HS512 подпись, TTL 15 минут, генерация (`generateAccessToken(User)`) и парсинг (`parseUserId(token)`). Секрет и TTL из `application.properties`
- **`JwtAuthenticationFilter.java`** — `OncePerRequestFilter`, читает Bearer token, парсит, грузит юзера, кладёт в SecurityContext через `UsernamePasswordAuthenticationToken`
- **`RestAuthenticationEntryPoint.java`** — кастомный 401 в нашем JSON-формате (`UNAUTHENTICATED`)

BCrypt для хеширования паролей через `PasswordEncoder` bean.

### TableBookApplication.java

```java
@SpringBootApplication(exclude = {
    UserDetailsServiceAutoConfiguration.class
})
```

Исключение нужно, чтобы Spring не создавал дефолтного in-memory юзера, который мешал кастомной аутентификации.

### application.properties — JWT секции

```properties
app.jwt.secret=${JWT_SECRET:dev-secret-change-this-in-production-must-be-at-least-256-bits-long}
app.jwt.access-token-ttl=PT15M
```

---

## Архитектурные решения, принятые в этой фазе

1. **JWT stateless** (без сессий) — стандарт REST. CSRF disabled.
2. **HS512** алгоритм подписи (симметричный, простой). RSA отложили — пока не нужно.
3. **TTL access-токена 15 минут** — короткий, безопасно. Refresh-токены отложили на потом.
4. **BCrypt** для паролей (cost factor дефолтный 10)
5. **Domain exceptions рядом с доменом** — `EmailAlreadyInUseException` в `auth/user/`, `InvalidCredentialsException` в `auth/login/`
6. **Защита от user enumeration** — одинаковая ошибка для "юзер не найден" и "пароль неверный" → 401 `INVALID_CREDENTIALS`
7. **DTOs как record, Entity как class** — record immutable, Entity нужен пустой конструктор и сеттеры для Hibernate
8. **API префикс `/api/v1/...`** — пишется явно в каждом контроллере, без констант
9. **`@AuthenticationPrincipal User`** — удобный способ получить текущего юзера в контроллере
10. **Регистрация → автологин** — после `POST /register` сразу выдаётся JWT (улучшает UX)

---

## Что выучили в этой фазе

1. **Bean и DI** — `@Component`, `@Service`, `@Repository`, `@RestController`, `@RequiredArgsConstructor`
2. **Layered architecture** — Controller → Service → Repository
3. **`@Transactional`** — readOnly на классе, override для write-методов; self-invocation problem
4. **Spring Data derived queries** — `findByEmail`, `existsByEmail` без SQL
5. **Bean Validation** — `@NotBlank`, `@Email`, `@Size`, `@Valid` в контроллере
6. **Spring Security как цепочка фильтров** — порядок матчит, `addFilterBefore`
7. **`SecurityContextHolder`** — ThreadLocal с аутентификацией текущего HTTP-запроса
8. **Stateless JWT vs sessions** — разные модели, разные подводные
9. **CSRF и почему disable для REST API** — атака через cookies, неактуально без них
10. **BCrypt и соль** — однонаправленный хеш, защита от rainbow tables, cost factor
11. **Иерархия исключений + полиморфизм в `@ExceptionHandler`** — один handler ловит всю ветку
12. **HTTP-семантика** — 200/201/400/401/403/404/409/500
13. **`@ResponseStatus(HttpStatus.CREATED)`** — переопределение дефолтного 200 для POST

---

## Грабли, через которые прошли

### 1. **Загадочный 401 при валидном токене (29 апр)**

Симптомы:
- Все логи показывают что фильтр аутентифицирует юзера правильно
- `Authentication` лежит в SecurityContext
- Но `/api/v1/me` всё равно возвращает 401

Я (Claude) **трижды** сменил гипотезы:
- `WebAuthenticationDetailsSource` — нет
- `UserDetailsServiceAutoConfiguration` — частично, но не полная причина
- Java 26 — нет (хотя переключение на 21 LTS правильное)

**Реальная причина:** Spring Boot на 404 делает **внутренний forward на `/error`**. Этот forward проходит через цепочку фильтров **снова**, но без токена в заголовках → SecurityContext пуст → 401.

**Почему именно 404:** контроллера `MeController` ещё не было. Создали → 404 ушёл → 401 исчез.

**Урок:** при дебаге Spring Security сначала **внимательно** читать логи. Если фильтр работает правильно — искать проблему **снаружи** фильтра.

### 2. Опечатка `JwtAuthenticateFilter` без `tion`

Несколько шагов жил с этим именем, потом сам Batyrkhan заметил и переименовал. Урок: `Refactor → Rename` — секундное действие.

### 3. Каша в пакете `auth/`

Изначально весь код жил в одном пакете. По мере роста — каша. Вовремя сделали рефакторинг на `auth/user/`, `auth/login/`, `auth/security/`. Если бы оставили "пока пусть так" — через две фазы было бы катастрофой.

### 4. Тех-долг в конце Фазы 1 (30 апр)

После прохождения основной логики осталось:
- `UserTestController` (тестовый CRUD юзеров без auth) — удалили
- `UserService.create(...)` дублировал логику — удалили
- `MeController` возвращал строку `"Hello, ..."` — переделали на `UserResponse`
- Регистрация была через тестовый endpoint — сделали `POST /api/v1/auth/register`

---

## Тестирование

Curl-сценарии для проверки:

```bash
# 1. Регистрация
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"jwt@test.com","password":"password123","fullName":"JWT Tester"}'
# → 201 + {"accessToken":"...", "tokenType":"Bearer"}

# 2. Логин
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jwt@test.com","password":"password123"}' \
  | jq -r '.accessToken')
# → 200 + token

# 3. /me с токеном
curl -i http://localhost:8080/api/v1/me \
  -H "Authorization: Bearer $TOKEN"
# → 200 + UserResponse JSON (без passwordHash!)

# 4. /me без токена → 401
# 5. /me с битым токеном → 401
# 6. Дубликат email при регистрации → 409 EMAIL_ALREADY_IN_USE
# 7. Невалидный пароль (< 8 символов) → 400 VALIDATION_FAILED
```

Все сценарии прошли.

---

## Артефакты фазы

В коде на конец фазы:

```
backend/src/main/java/com/tablebook/
├── TableBookApplication.java
├── auth/
│   ├── user/{User, UserRepository, UserService, PlatformRole, MeController, EmailAlreadyInUseException}
│   │   └── dto/UserResponse
│   ├── login/{AuthController, AuthService, InvalidCredentialsException}
│   │   └── dto/{LoginRequest, LoginResponse, RegisterRequest}
│   └── security/{SecurityConfig, JwtService, JwtAuthenticationFilter, RestAuthenticationEntryPoint}
└── shared/exception/{BusinessException, ResourceNotFoundException, GlobalExceptionHandler}
```

В БД на конец фазы:
- Таблица `users` с тестовым юзером `jwt@test.com`
- `flyway_schema_history` с V1

---

## Что отложили (тех-долги)

- **Refresh-токены** — пока не нужны, access TTL 15 минут терпимо
- **Phone-auth для гостей через Telegram** — отдельная Фаза P, перед Фазой 6
- **Тесты** — фаза тестирования после основного функционала
- **Audit-поля** (createdBy, updatedBy на entity) — когда понадобится
