# Spring шпаргалка — TableBook

> Личный конспект. Обновлено: 4 мая 2026, после закрытия Фазы 2.
> Связано с: [[README]] · [[architecture]] · [[progress-report]]

---

## 1. Beans и DI

**Bean** — объект, которым управляет Spring (а не ты через `new`).

| Способ        | Где                  | Когда                                                              |
|---------------|----------------------|--------------------------------------------------------------------|
| `@Component`  | На классе            | Свой класс без подходящего семантического имени                    |
| `@Service`    | На классе            | Бизнес-логика                                                      |
| `@Repository` | На классе/интерфейсе | Слой доступа к данным                                              |
| `@RestController` | На классе         | REST API контроллер                                                |
| `@Configuration` + `@Bean` | На методе | Чужой класс из библиотеки                                       |

**Получить bean:** `private final Type field;` + конструктор. С Lombok — `@RequiredArgsConstructor`.

---

## 2. Архитектура слоёв

```
HTTP-запрос
   ↓
@RestController (тонкий, только HTTP)
   ↓
@Service (бизнес-логика)
   ↓
@Repository (доступ к данным)
   ↓
PostgreSQL
```

DTO на границах:
- Request DTO (record, immutable)
- Response DTO (record, immutable)
- Entity (классы с сеттерами для Hibernate)

**Контроллер не лезет в репозиторий напрямую**. Только через сервис.

---

## 3. Транзакции и Dirty Checking

```java
@Service
@Transactional(readOnly = true)   // дефолт
public class MyService {

    public Entity find(Long id) { ... }    // readOnly от класса

    @Transactional                          // override для write
    public Entity update(Long id, ...) {
        Entity e = repo.findById(id)...;
        e.setField(newValue);               // НЕТ save() — Hibernate сам
        return e;                           // dirty checking → UPDATE при commit
    }
}
```

**Dirty checking** — Hibernate сравнивает текущее состояние managed entity с состоянием при загрузке. Изменённые поля → автоматический UPDATE при коммите транзакции.

**Без `@Transactional` это не работает** — entity отделится от сессии.

Применили на практике — см. [[phase-2-organizations-done]] (метод update в OrganizationService).

---

## 4. Spring Data Derived Queries

```java
public interface MyRepo extends JpaRepository<Entity, Long> {
    Optional<Entity> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsBySlugAndIdNot(String slug, Long id);  // SQL: WHERE slug=? AND id<>?
    List<Entity> findAllByUser(User user);
    Optional<Entity> findByUserAndOrganization(User u, Org o);
}
```

Бесплатно от JpaRepository: `save`, `findById`, `findAll`, `deleteById`, `count`, `existsById`.

**Операторы в derived queries:**
- `Equal`, `Not`, `IgnoreCase`
- `LessThan`, `GreaterThan`, `Between`, `After`, `Before`
- `Like`, `In`, `IsNull`, `IsNotNull`
- `OrderBy...Desc`, `OrderBy...Asc`

---

## 5. Custom JPQL через @Query

Когда derived queries не хватает (например, для JOIN FETCH):

```java
@Query("SELECT m FROM Membership m JOIN FETCH m.organization WHERE m.user = :user")
List<Membership> findAllByUserWithOrganization(@Param("user") User user);
```

`JOIN FETCH` — load связь сразу в одном SQL-запросе. Решает:
- **N+1 problem** (один запрос вместо 1+N)
- **LazyInitializationException** (organization уже загружена внутри транзакции)

История бага у нас — см. [[phase-2-organizations-done]] раздел "Грабли".

---

## 6. Entity (JPA маппинг)

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor    // обязательно пустой конструктор!
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)        // ВСЕГДА STRING, не ORDINAL!
    @Column(name = "platform_role", nullable = false)
    private PlatformRole platformRole = PlatformRole.USER;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
```

**Ловушки:**
- ❌ `record` для Entity — JPA нужен пустой конструктор и сеттеры
- ❌ `@Enumerated(EnumType.ORDINAL)` — ломается при добавлении нового элемента
- ❌ Имя поля `passwordHash` без `@Column(name = "password_hash")` — Schema-validation упадёт

---

## 7. JPA связи (@ManyToOne)

```java
@Entity
public class Organization {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}
```

**Правила:**
- **`fetch = FetchType.LAZY` ВСЕГДА на @ManyToOne** — иначе будут N+1 при каждой загрузке
- **`optional = false`** — соответствует `NOT NULL` в БД
- **`@JoinColumn(name = "...")`** — явное имя FK-колонки

**N:N связь со своими полями** (например role) → отдельная entity Membership с двумя `@ManyToOne`. НЕ `@ManyToMany`.

---

## 8. Flyway миграции

**Где:** `src/main/resources/db/migration/`
**Имя:** `V<число>__<описание>.sql` — два подчёркивания!

**Правила:**
- ✅ `ddl-auto=validate` в проде, Hibernate только валидирует
- ✅ Миграции в Git, новые изменения = новые файлы (`V2__...`, `V3__...`)
- ❌ Никогда не редактировать применённую миграцию (Flyway упадёт `checksum mismatch`)
- ❌ Не править БД руками через TablePlus в обход миграций

**Решение типичных проблем:**
- `Found more than one migration with version 1` → старый файл в `target/`. Решение: `mvn clean`. Встретили в [[phase-0-setup-done]].
- `Migration checksum mismatch` → отредактирована применённая. В dev: дропнуть схему

**SQL-операции в миграциях:**
```sql
-- Add column
ALTER TABLE users ADD COLUMN phone VARCHAR(50);

-- Drop column
ALTER TABLE users DROP COLUMN deprecated_field;

-- Rename column
ALTER TABLE users RENAME COLUMN full_name TO display_name;

-- Change type
ALTER TABLE users ALTER COLUMN phone TYPE VARCHAR(100);

-- Add index
CREATE INDEX idx_users_full_name ON users (full_name);

-- Add constraint
ALTER TABLE users ADD CONSTRAINT chk_phone_format
    CHECK (phone ~ '^\+?[0-9]{7,15}$');
```

---

## 9. Обработка ошибок

**Иерархия:**
```java
abstract class BusinessException extends RuntimeException {
    abstract HttpStatus getStatus();
    abstract String getCode();
}

class EmailAlreadyInUseException extends BusinessException { → 409 }
class InvalidCredentialsException extends BusinessException { → 401 }
class ResourceNotFoundException extends BusinessException { → 404 }
class ForbiddenException extends BusinessException { → 403 }
class SlugAlreadyTakenException extends BusinessException { → 409 }
```

**Глобальный обработчик:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(...);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(...) { /* 400 */ }
}
```

**Полиморфизм:** один handler ловит базовый класс — все наследники автоматически.

**HTTP-семантика:**

| Код | Значение                              | Когда                                |
|-----|---------------------------------------|---------------------------------------|
| 200 | OK                                    | Успешный GET, PATCH                   |
| 201 | Created                               | Успешный POST                         |
| 204 | No Content                            | Успешный DELETE                       |
| 400 | Bad Request                           | Невалидный запрос                     |
| 401 | Unauthorized (не залогинен)           | Нет JWT, битый JWT, неверный пароль   |
| 403 | Forbidden (залогинен, нет прав)       | Не член организации, не та роль       |
| 404 | Not Found                             | Ресурса нет                           |
| 409 | Conflict                              | Дубликат email/slug                   |
| 500 | Internal Server Error                 | Реальная бага                         |

---

## 10. Spring Security — JWT auth

См. полную реализацию в [[phase-1-auth-done]].

### Цепочка фильтров
```
HTTP → SecurityContextHolderFilter → CSRF → JwtAuthenticationFilter → ... → Authorization → Controller
```

### SecurityConfig — стандартный шаблон
```java
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### JWT-фильтр
```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(...) {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Long userId = jwtService.parseUserId(token);
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;

            var authority = new SimpleGrantedAuthority("ROLE_" + user.getPlatformRole().name());
            var auth = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            // битый/просроченный — анонимный запрос
        }
    }
}
```

### Получить текущего юзера в контроллере
```java
@GetMapping("/me")
public UserResponse me(@AuthenticationPrincipal User user) {
    return userService.toResponse(user);
}
```

### Authentication vs Authorization
- **401** — фильтр отклоняет (нет токена)
- **403** — сервис бросает `ForbiddenException` после проверки прав

---

## 11. Multi-tenancy ACL — паттерны

См. полную историю в [[phase-2-organizations-done]].

### Простая ручная проверка в сервисе
```java
@Transactional
public Org update(Long id, UpdateRequest request, User currentUser) {
    Org org = repo.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Org", id));

    Membership m = membershipRepo
        .findByUserAndOrganization(currentUser, org)
        .orElseThrow(() -> new ForbiddenException("Not a member"));

    if (m.getRole() != OrganizationRole.OWNER) {
        throw new ForbiddenException("Only OWNER can update");
    }

    // ... apply changes
    return org;
}
```

### Когда проверок становится много — `@PreAuthorize` SpEL
Будем добавлять в фазе 4-5, пока не нужно:
```java
@PreAuthorize("@orgSecurity.canManage(#orgId, authentication.principal)")
public Org update(Long orgId, ...) { ... }
```

---

## 12. JWT основы

**Структура токена:** `header.payload.signature`, всё в base64

**Payload (claims):**
```json
{
  "sub": "8",                    // subject (id юзера)
  "email": "user@test.com",
  "role": "USER",
  "iat": 1745000000,             // issued at
  "exp": 1745000900              // expiration
}
```

**Важно:**
- ❌ JWT — НЕ зашифровано. Любой может прочитать payload
- ✅ JWT — подписан. Подделать без секрета невозможно
- ✅ Кладём: id, role, email — публичные данные
- ❌ НЕ кладём: пароли, секреты, номера карт

**TTL access-токена:** 15 минут. Длиннее — опаснее.

---

## 13. BCrypt и пароли

```java
String hash = passwordEncoder.encode(rawPassword);          // INSERT
boolean ok = passwordEncoder.matches(input, hash);          // LOGIN
```

**Структура хеша:**
```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
└┬┘ └┬┘ └────────┬────────┘└─────────────┬───────────────┘
 │   │           │                      │
алгоритм cost   соль                    хеш
```

**Защита от user enumeration:** одинаковая ошибка для "юзера нет" и "пароль неверный" → 401 InvalidCredentialsException.

---

## 14. Bean Validation в DTO

### Создание (все поля обязательные)
```java
public record CreateOrganizationRequest(
    @NotBlank @Size(min = 2, max = 100) String name,
    @NotBlank @Size(min = 3, max = 50)
    @Pattern(
        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
        message = "must contain only lowercase letters, digits, and hyphens"
    )
    String slug
) {}
```

### Обновление (PATCH semantics, поля опциональны)
```java
public record UpdateOrganizationRequest(
    @Size(min = 2, max = 100) String name,        // нет @NotBlank
    @Size(min = 3, max = 50) @Pattern(...) String slug
) {}
```

`@Size`, `@Pattern` срабатывают **только если поле не null** — то что нужно для PATCH.

### Срабатывание
В контроллере добавить `@Valid`:
```java
@PostMapping
public Response create(@Valid @RequestBody CreateRequest request) { ... }
```

---

## 15. Кэши и проблемы (диагностика)

| Симптом                                              | Что чистить                                   |
|------------------------------------------------------|-----------------------------------------------|
| IDEA подсвечивает корректный код красным             | **File → Invalidate Caches and Restart**      |
| Изменил код — ведёт себя как раньше                  | `mvn clean` + перезапуск                      |
| Удалил файл, а оно всё ещё там                       | `mvn clean`                                    |
| `Found more than one migration with version`         | `mvn clean` (старый .sql в `target/classes/`) |
| Flyway checksum mismatch                             | Не редактируй применённые миграции            |
| 401 при валидном токене + тишина в логах             | Опечатка в URL контроллера (404→/error→401)   |
| LazyInitializationException                          | `JOIN FETCH` в репозитории                    |

---

## 16. Структура папок: by feature

```
com.tablebook/
├── auth/                  ← всё про аутентификацию
│   ├── login/             ← endpoint логина и связанное
│   ├── security/          ← JWT, фильтры, конфиги
│   └── user/              ← User-домен
│       └── dto/
├── organization/          ← organizations и memberships
│   └── dto/
├── shared/                ← общая инфраструктура
│   └── exception/         ← базовые exceptions + handler
└── (будущие фазы: restaurant/, booking/, ...)
```

**Domain exceptions — рядом с доменом:**
- `EmailAlreadyInUseException` → в `auth/user/`
- `SlugAlreadyTakenException` → в `organization/`

**Универсальные — в `shared/`:**
- `BusinessException` (абстрактная база)
- `ResourceNotFoundException` (универсальный 404)
- `ForbiddenException` (универсальный 403)

---

## 17. Полезные команды

```bash
# Maven
./mvnw clean              # удалить target/
./mvnw test               # тесты
./mvnw spring-boot:run    # запуск

# Docker
docker compose up -d
docker exec -it tablebook-postgres psql -U tablebook -d tablebook

# psql
\dt                  # таблицы
\d <table>           # структура
SELECT * FROM ...;
\q

# Получить токен
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"...","password":"..."}' | jq -r '.accessToken')

# Защищённый запрос
curl -i http://localhost:8080/api/v1/me -H "Authorization: Bearer $TOKEN"
```

---

## 18. Топ-правила, которые усвоились

1. **Никогда** не клади пароль/passwordHash в Response DTO
2. **Всегда** валидируй DTO через `@Valid` + bean validation
3. **Всегда** `OffsetDateTime` + `TIMESTAMPTZ`, не `LocalDateTime`
4. **Никогда** не редактируй применённую Flyway миграцию
5. **Никогда** `EnumType.ORDINAL` — всегда `EnumType.STRING`
6. **Всегда** возвращай `Optional<T>` из репозитория для "может не найти"
7. **Entity = mutable**, **DTO = record (immutable)**
8. **Stateless API** = `csrf.disable()` + `STATELESS` session policy
9. **JWT не шифрует**, только подписывает — не клади секреты
10. **404 на несуществующем эндпоинте** может ломать защиту через `/error` forward
11. **Domain exceptions** живут рядом с доменом
12. **`mvn clean`** перед паникой — половину проблем решает
13. **`@ManyToOne` всегда LAZY** + `JOIN FETCH` для list-кейсов
14. **PATCH без @NotBlank**, проверки `if (field != null)`
15. **Dirty checking** — UPDATE без `save()` внутри `@Transactional`
16. **403 через ForbiddenException**, не AccessDeniedException
17. **Authentication vs Authorization** — разные слои, разные коды
18. **Multi-tenancy ACL** — пока ручные проверки в сервисе
