# Implementación de los 69 endpoints en Spring Boot

> Guía para implementar en este backend (`es.devolvapp.backend`, Spring Boot 4, JPA + Flyway + PostgreSQL, Lombok, MapStruct, Spring Security) el contrato de `web/docs/API_ENDPOINTS.md`.
> Para cada endpoint: **qué archivos crear o tocar**, **qué tablas leer/escribir** y **qué devolver**.
> Las tablas son las de `V1__create_tables.sql`.

---

## 0. Antes de empezar: problemas a resolver en lo que ya existe

| Problema | Dónde | Qué hacer |
|---|---|---|
| La entidad `User` no coincide con la tabla `d_user` (usa `u_name`, `surname`, `u_role`, `wallet`, `street`, `isactive`…; la tabla tiene `first_name`, `last_name`, `password_hash`, `status`, `must_change_password`, `preferred_zone_id`, y roles/dirección en tablas aparte) | `entity/User.java` | Reescribir `User` según la tabla (ver §1.3). Los roles van en `user_role` (`@ElementCollection`) y la dirección en `address` (`@OneToOne`). El saldo **no** es una columna: se calcula (ver monedero). |
| `V2` y `V3` hacen `ALTER TABLE packet`, tabla que V1 no crea (V1 ya usa `bag` + `bag_content`). Flyway fallará en una BD limpia. | `db/migration/V2__*.sql`, `V3__*.sql` | Borrarlas si nadie las ha aplicado aún, o sustituirlas por migraciones vacías/no-op. Las migraciones nuevas deben empezar en `V4__`. |
| `UserController` usa `/api/v1/registro` y `/api/v1/login`; el front llama a `/api/auth/...`. | `controller/UserController.java` | Sustituirlo por `AuthController` (§2). |
| El login devuelve el usuario sin token y `SecurityConfig` no protege nada. | `config/SecurityConfig.java` | Añadir JWT (§1.2). |
| Los errores se devuelven como `{ error, details }`; el front espera `{ status, code, message, fieldErrors }`. | `exception/ApiExceptionHandler.java` | Reescribir (§1.1). |
| `CollectionOrderController` crea pedidos sin saber quién es el ciudadano. | `service/CollectionOrderService.java` | Obtener el ciudadano del token (`@AuthenticationPrincipal`). |

---

## 1. Infraestructura común (hacer primero)

### 1.1 Errores — `exception/`

| Archivo | Contenido |
|---|---|
| `ApiError.java` (record) | `int status, String code, String message, Map<String,String> fieldErrors` |
| `ApiException.java` | `RuntimeException` con `HttpStatus status` y `String code`. Subclases opcionales: `NotFoundException` (404 `NOT_FOUND`), `ConflictException` (409), `ForbiddenException` (403), `UnprocessableException` (422), `GoneException` (410). |
| `ApiExceptionHandler.java` | `@RestControllerAdvice`: `ApiException` → su status/code; `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR` + `fieldErrors` (clave = `error.getField()`, p. ej. `bolsas[0].cantidad`, `direccion.codigoPostal`); `AccessDeniedException` → 403 `FORBIDDEN`; `AuthenticationException` → 401. |

Códigos usados en el contrato: `BAD_CREDENTIALS`, `ACCOUNT_SUSPENDED`, `TOO_MANY_ATTEMPTS`, `VALIDATION_ERROR`, `DUPLICATE_EMAIL`/`DUPLICATE_DNI`/`DUPLICATE_TELEFONO`, `TOKEN_EXPIRED`, `TOKEN_USED`, `NO_PERMITIDO`, `FRANJA_COMPLETA`, `ZONA_SIN_SERVICIO`, `ESTADO_NO_CANCELABLE`, `SALDO_INSUFICIENTE`, `BOLSAS_SIN_VERIFICAR`, `ZONA_YA_ELEGIDA`, `SOLICITUD_PENDIENTE`, `YA_PROCESADA`, `ZONA_CON_RECOGIDAS`, `RECOLECTOR_OCUPADO`.

### 1.2 Seguridad JWT — `config/` y `security/`

Dependencias a añadir en `build.gradle`:
```gradle
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

| Archivo | Contenido |
|---|---|
| `security/JwtService.java` | Genera/valida access token (subject = id de usuario, claim `roles`), `expiresIn` en segundos. |
| `security/JwtAuthFilter.java` | `OncePerRequestFilter`: lee `Authorization: Bearer`, carga el usuario, pone `UsernamePasswordAuthenticationToken` con authorities `ROLE_CIUDADANO`… |
| `security/AuthUser.java` | Principal (`id`, `email`, `roles`). En los controladores: `@AuthenticationPrincipal AuthUser me`. |
| `security/LoginAttemptService.java` | Contador de intentos fallidos en Redis (ya está la dependencia) → 429 `TOO_MANY_ATTEMPTS`. |
| `config/SecurityConfig.java` | `STATELESS`; `permitAll` en `/api/auth/login`, `/api/auth/registro`, `/api/auth/password/**`; `hasRole("CIUDADANO")` en `/api/ciudadano/**`, `hasRole("RECOLECTOR")` en `/api/recolector/**`, `hasRole("ADMIN")` en `/api/admin/**`; `authenticated()` el resto. Bean `PasswordEncoder` = `BCryptPasswordEncoder`. CORS: con credenciales no se puede usar `*`, poner el origen del front. |

### 1.3 Entidades JPA — `entity/` (una por tabla)

| Entidad | Tabla | Notas |
|---|---|---|
| `User` (reescribir) | `d_user` | `firstName, lastName, email, idCard, phoneNumber, passwordHash, mustChangePassword, status (UserStatus), preferredZone (@ManyToOne Zone), createdAt, updatedAt`; `@ElementCollection @CollectionTable(name="user_role") @Enumerated Set<Role> roles`; `@OneToOne(mappedBy="user") Address address` |
| `Address` | `address` | PK = `user_id` (`@MapsId`) |
| `RefreshToken` | `refresh_token` | |
| `PasswordResetToken` | `password_reset_token` | |
| `Zone` | `zone` | |
| `ZipCode` | `zip_code` | `@ManyToOne Zone zone` (nullable) |
| `ZoneDayCollector` | `zone_day_collector` | PK compuesta `(zoneId, dayOfWeek)` con `@IdClass`/`@EmbeddedId` |
| `TimeSlot` | `time_slot` | `active` = borrado lógico |
| `ZoneChangeRequest` | `zone_change_request` | |
| `CollectionOrder` (existe) | `collection` | Pasar `zoneId/collectorId/timeSlotId` a `@ManyToOne` (Zone, User, TimeSlot) facilita las consultas |
| `Bag`, `BagContent`, `ContainerCatalog` (existen) | `bag`, `bag_content`, `container_type` | |
| `Settlement` | `settlement` | `@OneToOne CollectionOrder` |
| `IncidentType` | `incident_type` | |
| `Incident` | `incident` | |
| `IncidentHistory` | `incident_history` | |
| `AppParameter` | `app_parameter` | |
| `Withdrawal` | `withdrawal` | |
| `WithdrawalHistory` | `withdrawal_history` | |
| `Remittance` | `remittance` | |

Enums nuevos: `UserStatus (ACTIVA, SUSPENDIDA)`, `CollectionStatus (PENDIENTE, EN_CAMINO, COMPLETADA, CANCELADA)`, `BagResult (ACEPTADA, DISCREPANCIA, NO_ENCONTRADA)`, `IncidentStatus (ABIERTA, EN_REVISION, RESUELTA)`, `WithdrawalStatus (PENDIENTE, COMPLETADA, FALLIDA)`, `ZoneRequestStatus (PENDIENTE, APROBADA, RECHAZADA, CANCELADA)`.

### 1.4 Repositorios — `repository/`

Uno por entidad (`extends JpaRepository<X, Long>`; los que filtran listados paginados también `JpaSpecificationExecutor<X>`). Los métodos concretos se indican en cada endpoint.

### 1.5 Utilidades compartidas — `service/` y `dto/common/`

| Archivo | Para qué |
|---|---|
| `dto/common/PageResponse<T>` (record) | `content, totalElements, totalPages, number, size` — construir desde `Page<T>` (no serializar `Page` directamente: Spring 3+ avisa de que su JSON no es estable). |
| `service/ParameterService` | Lee `app_parameter` (cacheable) → `importePorEnvase`, `porcentajeCiudadano`, `porcentajeEmpresa`, `maxBolsas`, `kgCo2PorKmCoche`, `kgCo2PorArbolAnio`. |
| `service/CodeGenerator` | Códigos: pedido `P-1001`, bolsa `B-7F3K2`, incidencia `INC-2001`, retirada `R-501`, referencia `TRF-AAAAMMDD-000123`, remesa `REM-AAAAMMDD-HHMMSS`. Usar una `SEQUENCE` de Postgres por tipo (migración `V4__sequences.sql`). |
| `service/WalletService` | `saldo(citizenId) = SUM(settlement.citizen_share de sus collection) − SUM(withdrawal.amount WHERE status <> 'FALLIDA')` |
| `service/ImpactService` | Cálculos de CO2 compartidos por ciudadano, recolector y admin (desde `settlement.co2_kg` y `bag_content` × `container_type.co2_kg_per_container`). |
| `service/SettlementService` | Liquidación al completar una recogida (HU-05.02). |
| `mappers/*Mapper` | MapStruct, uno por agregado (`UserMapper`, `CollectionMapper`, `IncidentMapper`, `WithdrawalMapper`, `ZoneMapper`). |

### 1.6 DTOs reutilizados en muchos endpoints — `dto/common/`

| DTO | Campos | Origen BD |
|---|---|---|
| `UsuarioResponse` | `id, nombre, apellidos, email, telefono, dni, roles ["ROLE_..."], estado, debeCambiarPassword, direccion (DireccionDto \| null), zonaPreferida (ZonaRef \| null)` | `d_user` + `user_role` + `address` + `zone` |
| `DireccionDto` | `tipoVia, nombreVia, numero, piso, letra, codigoPostal, ciudad, lat?, lng?` | `address` (o columnas `addr_*` de `collection`) |
| `ZonaRef` | `id, nombre` | `zone` |
| `PersonaRef` | `id, nombre` (nombre + apellidos) | `d_user` |
| `BolsaDto` | `id, codigo, tipoEnvase, cantidad, capacidadMaxima, resultado, envasesValidos, nota?` | `bag` + `bag_content` (`tipoEnvase` = el código si hay un solo contenido, `MEZCLADO` si hay varios) |
| `IncidenciaResumenDto` | `id, codigo, tipo, estado, creadaEn, resolucion` | `incident` |
| `LiquidacionDto` | `envasesValidos, importeTotal, importeCiudadano, parteEmpresa, co2Kg` | `settlement` |
| `ImpactoResponse` | `co2EvitadoKg, envasesReciclados, recogidas, porTipo [{tipoEnvase, envases, co2Kg}], porMes [{mes, co2Kg}], zona? {nombre, co2EvitadoKg}` | ver §5 |

---

## 2. `authService.js` → `AuthController` (`/api/auth`)

**Archivos:** `controller/AuthController.java`, `service/AuthService.java`, `service/PasswordResetService.java`, `service/MailService.java` (envío del enlace; puede ser un log al principio), DTOs en `dto/auth/`: `LoginRequest`, `LoginResponse`, `RegistroRequest` (+ `DireccionDto` con `@Valid`), `RecuperarRequest`, `RestablecerRequest`. Repos: `UserRepository`, `RefreshTokenRepository`, `PasswordResetTokenRepository`, `ZipCodeRepository`.

| Endpoint | Lee de BD | Escribe en BD | Devuelve |
|---|---|---|---|
| `POST /login` | `UserRepository.findByEmailIgnoreCaseOrIdCardIgnoreCase(username, username)` + `user_role`. Comparar con `passwordEncoder.matches`. Si `status=SUSPENDIDA` → 403. | `refresh_token` (guardar **hash** del refresh token, `expires_at`) | 200 `LoginResponse { accessToken, refreshToken, tokenType:"Bearer", expiresIn, usuario: UsuarioResponse }` · 401 `BAD_CREDENTIALS` · 403 `ACCOUNT_SUSPENDED` · 429 |
| `POST /logout` | `refresh_token` del usuario | `revoked_at = now()` | 204 |
| `GET /me` | `d_user` + `user_role` + `address` + `zone` (por id del token) | — | 200 `UsuarioResponse` |
| `POST /registro` | `existsByEmail`, `existsByIdCard`, `existsByPhoneNumber`; `zip_code` (el CP debe existir; si no → 400 `fieldErrors.direccion.codigoPostal`) | `d_user` (password BCrypt), `user_role` = `CIUDADANO` **siempre**, `address` (lat/lng del `zip_code` como aproximación) | 201 `UsuarioResponse` · 400 · 409 `DUPLICATE_*` con `fieldErrors` |
| `POST /password/recuperar` | `findByEmail` | `password_reset_token` (hash de un UUID, `expires_at = now()+30min`); enviar correo | 202 **siempre**, sin cuerpo |
| `GET /password/token/{token}` | `PasswordResetTokenRepository.findByTokenHash(hash(token))` | — | 200 · 410 `TOKEN_EXPIRED` / `TOKEN_USED` (404 si no existe) |
| `POST /password/restablecer` | igual que el anterior | `d_user.password_hash`, `password_reset_token.used_at`, revocar sus `refresh_token` | 204 |

> Falta en el contrato `POST /api/auth/refresh` (canjear refresh token). Conviene añadirlo aunque el front aún no lo use.

---

## 3. `perfilService.js` → `PerfilController` (`/api/perfil`)

**Archivos:** `controller/PerfilController.java`, `service/PerfilService.java`, `dto/perfil/ActualizarPerfilRequest`, `CambiarPasswordRequest`, `PasswordInicialRequest`, `PerfilActualizadoResponse` (`UsuarioResponse` + `avisoZona`).

| Endpoint | Lee | Escribe | Devuelve |
|---|---|---|---|
| `GET /` | `d_user`, `user_role`, `address`, `zone` | — | `UsuarioResponse` |
| `PUT /` | Unicidad de email/teléfono excluyendo al propio usuario; `zip_code` (existe y su `zone_id`) | `d_user` (`first_name, last_name, email, phone_number, updated_at`); `address` solo si tiene rol CIUDADANO (lat/lng = geocodificación o la del CP). **No** tocar `collection.addr_*` | `UsuarioResponse + avisoZona` (texto si el nuevo CP tiene `zone_id = NULL` o cambia de zona, si no `null`) · 400 · 409 |
| `PUT /password` | `password_hash` | `password_hash` | 204 · 400 si `actual` no coincide |
| `PUT /password-inicial` | `must_change_password` | `password_hash`, `must_change_password=false` | `UsuarioResponse` · 409 `NO_PERMITIDO` |

---

## 4. `configService.js` → `ConfigController` (`/api/config`, `/api/catalogo`)

**Archivos:** `controller/ConfigController.java`, `service/CatalogoService.java` (+ `ParameterService`), `dto/config/ParametrosResponse`, `TipoEnvaseDto`, `TipoIncidenciaDto`, `CodigoPostalDto`. Repos: `AppParameterRepository`, `ContainerCatalogRepository` (existe), `IncidentTypeRepository`, `ZipCodeRepository`.

| Endpoint | Lee | Devuelve |
|---|---|---|
| `GET /api/config/parametros` | `app_parameter` (todas) + `container_type.co2_kg_per_container` | `{ economia: { importePorEnvase, porcentajeCiudadano, porcentajeEmpresa, provisional }, pedidos: { maxBolsas }, impacto: { kgCo2PorEnvase: { PET: 0.03, ... }, kgCo2PorKmCoche, kgCo2PorArbolAnio } }`. `provisional` puede ser otro parámetro (`importe_provisional`, añadir en V4) |
| `GET /api/catalogo/tipos-envase` | `container_type` | `[{ codigo, nombre, icono, capacidadMaximaBolsa }]` |
| `GET /api/catalogo/tipos-incidencia` | `incident_type` | `[{ codigo, nombre }]` (añadir datos base en V4: `CIUDADANO_AUSENTE`, `BOLSA_NO_ENCONTRADA`, `DISCREPANCIA`, `OTRO`…) |
| `GET /api/catalogo/codigos-postales` | `zip_code` | `[{ cp, barrio, lat, lng }]` (añadir en V4 el `INSERT` de los CP de Málaga) |

Todo esto es candidato a `@Cacheable` (Redis ya está).

---

## 5. `ciudadanoService.js` → `CiudadanoController` (`/api/ciudadano`)

**Archivos:** `controller/CiudadanoPedidoController.java` (sustituye/amplía `CollectionOrderController`), `controller/CiudadanoMonederoController.java`, `controller/CiudadanoImpactoController.java`; `service/CollectionOrderService.java` (existe, ampliar), `service/DisponibilidadService.java`, `WalletService`, `ImpactService`; DTOs en `dto/ciudadano/`: `PedidoResumenDto`, `PedidoDetalleDto`, `CrearPedidoRequest` (renombrar/adaptar `CreateCollectionRequest`: `bolsas [{tipoEnvase, cantidad}], fecha, franjaId`), `DisponibilidadResponse`, `HuecoDto`, `MonederoResponse`, `MovimientoDto`, `RetiradaRequest`, `RetiradaResponse`.

| Endpoint | Lee | Escribe | Devuelve |
|---|---|---|---|
| `GET /pedidos?estado&page&size` | `collection WHERE citizen_id = me` (+ `status IN (PENDIENTE, EN_CAMINO)` si `ACTIVOS`), orden `collection_date DESC`; por cada uno: nº de `bag`, `SUM(bag.quantity)`, `settlement.citizen_share`, `EXISTS incident` | — | `PageResponse<PedidoResumenDto { id, codigo, estado, creadoEn, fecha, horaInicio, horaFin, numBolsas, totalEnvases, importeCiudadano, tieneIncidencias }>`. Hacerlo con una consulta JPQL con *constructor expression* / proyección para evitar N+1 |
| `GET /pedidos/{id}` | `collection` (404 si no es suya), `bag` + `bag_content`, `settlement`, `incident` | — | resumen + `{ direccion (addr_*), iniciadoEn, completadoEn, canceladoEn, cancelable (= estado PENDIENTE), bolsas [BolsaDto], liquidacion { envasesValidos, importeCiudadano } \| null, incidencias [IncidenciaResumenDto] }` |
| `GET /disponibilidad` | `address.zip_code` del ciudadano → `zip_code.zone_id` → `time_slot WHERE zone_id AND active`; para los próximos 14 días, cruzar `day_of_week` (1=lunes, `LocalDate.getDayOfWeek().getValue()`) y contar `collection WHERE time_slot_id AND collection_date AND status <> 'CANCELADA'` | — | `{ codigoPostal, zona { id, nombre } \| null, huecos [{ fecha, franjaId, horaInicio, horaFin, plazasLibres = capacity − ocupadas, completa }] }`. Opcional: excluir días sin fila en `zone_day_collector` |
| `POST /pedidos` | `container_type` (capacidad), `ParameterService.maxBolsas`, `address` + `zip_code` (zona; si `NULL` → 422 `ZONA_SIN_SERVICIO`), `time_slot` (debe ser de esa zona y de ese día), plazas ocupadas (bloquear con `@Lock(PESSIMISTIC_WRITE)` sobre el `time_slot`) → 409 `FRANJA_COMPLETA`; `zone_day_collector` para asignar `collector_id` | `collection` (código, copia de dirección y horas de la franja, `collector_id`), `bag` (código, `max_capacity`), `bag_content` | 201 `PedidoDetalleDto` · 400 (`fieldErrors "bolsas[0].cantidad"` si supera la capacidad) · 409 · 422 |
| `POST /pedidos/{id}/cancelar` | `collection` (suya) | `status=CANCELADA, cancelled_at, cancelled_by=me` | 200 `PedidoDetalleDto` · 409 `ESTADO_NO_CANCELABLE` si no es PENDIENTE |
| `GET /impacto` | `settlement s JOIN collection c WHERE c.citizen_id = me`: `SUM(co2_kg)`, `SUM(valid_containers)`, `COUNT`; por tipo: `bag_content JOIN bag JOIN collection (COMPLETADA)` × `container_type.co2_kg_per_container`; por mes: `date_trunc('month', c.completed_at)`; zona: `SUM(co2_kg)` de las recogidas de su zona | — | `ImpactoResponse` |
| `GET /monedero` | `WalletService.saldo(me)` | — | `{ saldoDisponible }` |
| `GET /monedero/movimientos?page&size` | Unión de **abonos** (`settlement` de sus recogidas → `tipo ABONO, fecha = created_at, importe = citizen_share, estado COMPLETADA, concepto "Recogida P-1001"`) y **retiradas** (`withdrawal` → `tipo RETIRADA, importe, estado, referencia = bank_reference, motivoFallo = failure_reason, procesadaEn`). Más fácil con una consulta nativa `UNION ALL ... ORDER BY fecha DESC LIMIT/OFFSET` + `COUNT` | — | `PageResponse<MovimientoDto>` |
| `POST /monedero/retiradas` | `WalletService.saldo(me)` (si `importe > saldo` → 422 `SALDO_INSUFICIENTE`). Validar IBAN (anotación propia `@Iban`, checksum mod-97) | `withdrawal` (PENDIENTE), `withdrawal_history` | 201 `{ id, codigo, estado:"PENDIENTE", importe, saldoDisponible }` |

---

## 6. `recolectorService.js` → `RecolectorController` (`/api/recolector`)

**Archivos:** `controller/RecolectorRecogidaController.java`, `controller/RecolectorPreferenciasController.java`; `service/RecolectorRecogidaService.java`, `service/RutaService.java`, `service/ZoneRequestService.java` (compartido con admin), `SettlementService`, `IncidentService` (compartido con admin); DTOs en `dto/recolector/`: `RecogidasResponse`, `AsignacionDto { zona, dias }`, `RecogidaResumenDto`, `RecogidaDetalleDto`, `ResultadoBolsaRequest`, `IncidenciaRequest`, `IncidenciaCreadaResponse`, `RutaResponse`, `ParadaDto`, `HistorialItemDto`, `PreferenciasResponse`, `SolicitudZonaDto`, `ElegirZonaRequest`, `SolicitudZonaRequest`.

| Endpoint | Lee | Escribe | Devuelve |
|---|---|---|---|
| `GET /recogidas` | `zone_day_collector WHERE collector_id = me` (agrupar días por zona); `collection WHERE collector_id = me AND (collection_date >= hoy OR status = 'EN_CAMINO') AND status <> 'CANCELADA'` + nº bolsas y envases | — | `{ asignaciones [{ zona, dias }], recogidas [{ id, codigo, estado, fecha, horaInicio, horaFin, direccion, lat, lng, zona, numBolsas, totalEnvases }] }` |
| `GET /recogidas/{id}` | `collection` (si `collector_id <> me` → 403), `d_user` del ciudadano, `bag` + `bag_content`, `incident` | — | resumen + `{ ciudadano { nombre, telefono }, iniciadoEn, completadoEn, bolsas [BolsaDto con nota], incidencias }` |
| `POST /recogidas/{id}/iniciar` | `collection` (suya, PENDIENTE; si no → 409) | `status=EN_CAMINO, started_at=now()` | `RecogidaDetalleDto` |
| `PUT /recogidas/{id}/bolsas/{bolsaId}` | `collection` (suya, EN_CAMINO), `bag` (de esa recogida) | `bag.result, valid_containers` (ACEPTADA → `= quantity`; NO_ENCONTRADA → 0; DISCREPANCIA → el enviado, obligatorio y `<= quantity`), `note`. Si DISCREPANCIA/NO_ENCONTRADA: `incident` (con `bag_id`, `created_by = me`) + `incident_history` | `BolsaDto` |
| `POST /recogidas/{id}/completar` | `collection` (suya, EN_CAMINO); si alguna `bag.result IS NULL` → 409 `BOLSAS_SIN_VERIFICAR`; `ParameterService`; `bag_content` × `container_type` para CO2 | `collection.status=COMPLETADA, completed_at`; `settlement` (`valid_containers = SUM(bag.valid_containers)`, `total_amount = válidos × importePorEnvase`, `citizen_share`, `company_share`, `co2_kg`). Todo en una `@Transactional`; `settlement.collection_id` es UNIQUE → idempotente | `RecogidaDetalleDto` (con liquidación) |
| `POST /recogidas/{id}/incidencias` | `collection` (suya), `incident_type` (existe) | `incident` + `incident_history ("Creada")` | 201 `{ id, codigo, creadaEn }` |
| `GET /ruta?fecha&lat&lng` | `collection WHERE collector_id = me AND collection_date = fecha` (todas, para contar `hechas` y `canceladas`). Origen: `lat/lng` si vienen, si no la nave (parámetros `nave_nombre/nave_lat/nave_lng` en `app_parameter`) | — | Agrupar por `start_time`; dentro de cada franja, vecino más cercano (Haversine) desde el punto anterior. `{ fecha, origen { nombre, lat, lng }, paradas [{ orden, ...recogida, distanciaDesdeAnteriorKm }], hechas, canceladas, distanciaKm, duracionMin }` (duración = km / velocidad media + minutos por parada) |
| `GET /historial?page` | `collection WHERE collector_id = me AND status = 'COMPLETADA'` + `settlement` + `EXISTS incident`, orden `completed_at DESC` | — | `PageResponse<resumen + { completadoEn, envasesValidos, co2EvitadoKg, tieneIncidencias }>` |
| `GET /impacto` | Igual que el del ciudadano filtrando `c.collector_id = me` | — | `ImpactoResponse` con `zona = null` |
| `GET /preferencias` | `d_user.preferred_zone_id`, `zone_day_collector` del recolector, todas las `zone` + sus `zip_code`, `zone_change_request WHERE collector_id = me` (orden `created_at DESC`) | — | `{ zonaPreferida, asignaciones, zonas [{ id, nombre, codigosPostales }], solicitudes [SolicitudZonaDto] }` |
| `PUT /preferencias/zona` | `preferred_zone_id` (si no es null → 409 `ZONA_YA_ELEGIDA`), `zone` | `d_user.preferred_zone_id` | `PreferenciasResponse` |
| `POST /solicitudes-zona` | `existsByCollectorIdAndStatus(me, PENDIENTE)` → 409 `SOLICITUD_PENDIENTE` | `zone_change_request` (`current_zone_id = preferred_zone_id`) | 201 `SolicitudZonaDto` |
| `POST /solicitudes-zona/{id}/cancelar` | la solicitud (suya, PENDIENTE; si no → 409) | `status = CANCELADA` | `SolicitudZonaDto` |

---

## 7. `adminService.js` → controladores `/api/admin`

Dividir por recurso para no tener un controlador gigante. Todos con `@PreAuthorize("hasRole('ADMIN')")` o protegidos por URL en `SecurityConfig`.

### 7.1 Panel e indicadores — `AdminDashboardController`, `service/AdminDashboardService`, `ImpactService`

| Endpoint | Lee | Devuelve |
|---|---|---|
| `GET /pendientes` | `withdrawal WHERE status=PENDIENTE` (`COUNT`, `SUM(amount)`); `zone_change_request PENDIENTE` (count); `incident WHERE status <> RESUELTA` (count); `collection WHERE collection_date < hoy AND status IN (PENDIENTE, EN_CAMINO)` (atrasadas); `collection WHERE collector_id IS NULL AND status = PENDIENTE`; zonas con `time_slot` activo en un `day_of_week` sin fila en `zone_day_collector` | `{ transferencias { total, importe }, solicitudesZona, incidenciasAbiertas, recogidasAtrasadas, recogidasSinRecolector, diasSinRecolector [{ zona, diaSemana }] }` |
| `GET /indicadores?desde&hasta` | En el rango (`collection_date` o `created_at`): `collection` por estado; `incident` (registradas en el rango, abiertas); `settlement` (`SUM valid_containers, total_amount, company_share, citizen_share, co2_kg`); impacto por tipo/mes como en §5; `SUM(co2_kg)` histórico; `SUM(co2_kg) GROUP BY zone` | `{ recogidas { solicitadas, completadas, canceladas, enCurso }, incidencias { registradas, abiertas }, economia { envasesValidos, importeTotal, parteEmpresa, parteCiudadanos }, impacto { co2EvitadoKg, envasesReciclados, porTipo, porMes, totalHistoricoKg }, porZona [{ zona, co2Kg }] }` |

### 7.2 Incidencias — `AdminIncidenciaController`, `service/IncidentService`

DTOs: `IncidenciaAdminResumenDto`, `IncidenciaAdminDetalleDto`, `ActualizarIncidenciaRequest`, `HistorialDto { fecha, autor, accion }`.

| Endpoint | Lee | Escribe | Devuelve |
|---|---|---|---|
| `GET /incidencias?tipo&estado&page&size` | `incident` (Specification por `type`, `status`) JOIN `incident_type` y `collection` | — | `PageResponse<{ id, codigo, tipo, tipoNombre, estado, creadaEn, pedido { id, codigo } }>` |
| `GET /incidencias/{id}` | `incident`, `incident_history` (+ nombre del autor), `collection` + ciudadano + recolector + `bag` + `settlement` | — | resumen + `{ descripcion, bolsaId, resolucion, resueltaEn, historial, recogida { id, codigo, estado, fecha, horaInicio, horaFin, direccion, ciudadano, recolector, bolsas, liquidacion } }` |
| `PATCH /incidencias/{id}` | `incident` | `status`, `resolution`, `resolved_at` (si pasa a RESUELTA; sin `resolucion` → 400, la tabla tiene un CHECK); `incident_history (author_id = me, action = "Estado: EN_REVISION" / "Resolución actualizada")` | detalle |

### 7.3 Recogidas — `AdminRecogidaController`, `service/AdminRecogidaService`

| Endpoint | Lee | Escribe | Devuelve |
|---|---|---|---|
| `GET /recogidas?fecha&estado&zonaId&q&page` | `collection` + `zone` + ciudadano + recolector (Specification). `ATRASADAS` = `collection_date < hoy AND status IN (PENDIENTE, EN_CAMINO)`; `SIN_RECOLECTOR` = `collector_id IS NULL AND status = PENDIENTE`; `q` busca en `code`, nombre del ciudadano y `addr_street_name` (`ILIKE`) | — | `PageResponse<{ id, codigo, estado, fecha, horaInicio, horaFin, zona, direccion, ciudadano, recolector \| null, numBolsas, totalEnvases, tieneIncidencias }>` |
| `GET /recogidas/{id}` | igual + `bag`, `settlement`, `incident` | — | resumen + `{ creadoEn, iniciadoEn, completadoEn, canceladoEn, bolsas, liquidacion, incidencias }` |
| `PUT /recogidas/{id}/recolector` | `collection` (no COMPLETADA/CANCELADA, si no → 409), `d_user` con rol RECOLECTOR y ACTIVA | `collection.collector_id` | detalle |
| `POST /recogidas/{id}/cancelar` | `collection` (PENDIENTE o EN_CAMINO) | `status=CANCELADA, cancelled_at, cancelled_by = me` | detalle |

### 7.4 Transferencias — `AdminTransferenciaController`, `service/WithdrawalService`

| Endpoint | Lee | Escribe | Devuelve |
|---|---|---|---|
| `GET /transferencias?estado&page&size` | `withdrawal` + ciudadano; además `SELECT status, COUNT(*) GROUP BY status` | — | `PageResponse<{ id, codigo, ciudadano, importe, estado, solicitadaEn }>` **+** `totales { PENDIENTE, COMPLETADA, FALLIDA }` (DTO `TransferenciasPageResponse extends PageResponse`) |
| `GET /transferencias/{id}` | `withdrawal`, `withdrawal_history` | — | resumen + `{ iban, procesadaEn, referenciaBancaria, motivoFallo, historial [{ fecha, estado }] }` |
| `POST /transferencias/{id}/resultado` | `withdrawal` con `@Lock(PESSIMISTIC_WRITE)`; si no es PENDIENTE → 409 `YA_PROCESADA` | COMPLETADA: `bank_reference = TRF-AAAAMMDD-000{id}`, `processed_at`, `processed_by`. FALLIDA: `failure_reason` (obligatorio) — el saldo vuelve solo porque `WalletService` ignora las FALLIDAS. Siempre `withdrawal_history` | detalle |
| `POST /transferencias/pagar-lote` | `withdrawal WHERE id IN ids` con lock; si alguna no está PENDIENTE → 409 y no se toca nada | `remittance` (código, `transfer_count`, `total`, `created_by`); cada `withdrawal` → COMPLETADA + `bank_reference` + `remittance_id`; `withdrawal_history` | `{ procesadas, remesa { id, codigo, total } }` |

### 7.5 Zonas, franjas y recolectores — `AdminZonaController`, `AdminFranjaController`, `service/ZoneService`, `service/TimeSlotService`

DTOs: `ZonaAdminDto`, `DiaZonaDto { diaSemana, recolector, franjas }`, `FranjaDto`, `ZonaRequest { nombre, codigosPostales }`, `FranjaRequest`, `AsignarDiaRequest { recolectorId }`, `RecolectorDisponibleDto`, `CodigoPostalZonaDto`.

| Endpoint | Lee | Escribe | Devuelve |
|---|---|---|---|
| `GET /codigos-postales` | `zip_code` LEFT JOIN `zone` | — | `[{ cp, barrio, zona \| null }]` |
| `GET /zonas` | `zone`, sus `zip_code`, `zone_day_collector` + recolector, `time_slot` activos; `pendientes` = `COUNT collection PENDIENTE` de la zona | — | `[{ id, nombre, codigosPostales, pendientes, dias [1..7: { diaSemana, recolector \| null, franjas [{ id, horaInicio, horaFin, capacidad }] }] }]` |
| `GET /recolectores` | `d_user` con rol RECOLECTOR y ACTIVA, `preferred_zone_id`, `zone_day_collector` | — | `[{ id, nombre, zonaPreferida, diasOcupados [{ diaSemana, zona, zonaId }] }]` |
| `POST /zonas` | `zone.name` único; los CP no deben tener otra zona (409) | `zone`; `zip_code.zone_id` | 201 `ZonaAdminDto` |
| `PUT /zonas/{id}` | idem excluyendo la propia | `zone.name`; `zip_code.zone_id = NULL` para los quitados, `= id` para los nuevos | `ZonaAdminDto` |
| `DELETE /zonas/{id}` | `existsByZoneIdAndStatusIn(id, [PENDIENTE, EN_CAMINO])` → 409 `ZONA_CON_RECOGIDAS` | `DELETE zone` (CASCADE borra días y franjas; `zip_code.zone_id` → NULL). Ojo: `collection.zone_id` es `RESTRICT`, si hay recogidas históricas fallará → mejor borrado lógico (añadir `zone.active` en V4) | 204 |
| `PUT /zonas/{id}/dias/{diaSemana}/recolector` | `zone_day_collector WHERE collector_id AND day_of_week AND zone_id <> id` → 409 `RECOLECTOR_OCUPADO` | `zone_day_collector` (upsert; `recolectorId = null` → delete); `UPDATE collection SET collector_id WHERE zone_id = id AND status = PENDIENTE AND EXTRACT(ISODOW FROM collection_date) = diaSemana` | `ZonaAdminDto` |
| `POST /zonas/{id}/franjas` | `zone` | `time_slot` | 201 `FranjaDto` |
| `PUT /franjas/{id}` | `time_slot` | `start_time, end_time, capacity` (las recogidas ya creadas tienen su copia de horas, no cambian) | `FranjaDto` |
| `DELETE /franjas/{id}` | `time_slot` | `active = false` (borrado lógico; no cancelar recogidas) | 204 |

### 7.6 Solicitudes de cambio de zona — `AdminSolicitudZonaController`, `ZoneRequestService`

| Endpoint | Lee | Escribe | Devuelve |
|---|---|---|---|
| `GET /solicitudes-zona?estado` | `zone_change_request` + recolector + zonas + `resolved_by` | — | `[{ id, estado, creadaEn, motivo, recolector, zonaActual, zonaSolicitada, resueltaEn, resueltaPor, respuesta }]` |
| `POST /solicitudes-zona/{id}/resolver` | la solicitud (PENDIENTE, si no → 409); si `moverDias`: `zone_day_collector` del recolector y de la zona destino | `status APROBADA/RECHAZADA, resolved_at, resolved_by, response`; si aprueba: `d_user.preferred_zone_id`; si `moverDias`: mover cada día cuyo hueco esté libre en la zona nueva | `{ solicitud, diasEnConflicto [diaSemana] }` |

### 7.7 Usuarios — `AdminUsuarioController`, `service/AdminUserService`

DTOs: `UsuarioAdminResumenDto`, `UsuarioAdminDetalleDto`, `EditarUsuarioRequest`, `CrearRecolectorRequest`, `PasswordProvisionalResponse`.

| Endpoint | Lee | Escribe | Devuelve |
|---|---|---|---|
| `GET /usuarios?q&rol&page&size` | `d_user` + `user_role` (Specification: `q` en nombre/apellidos/email/dni con `ILIKE`; `rol` con `JOIN roles`) | — | `PageResponse<{ id, nombre, email, roles, estado }>` (aquí `roles` sin prefijo `ROLE_`) |
| `GET /usuarios/{id}` | `d_user`, `user_role`, `address`, zona del CP; si ciudadano: `COUNT collection` y `SUM(settlement.co2_kg)`; si recolector: `preferred_zone`, `zone_day_collector` y su CO2 | — | resumen + `{ nombrePila, apellidos, dni, telefono, direccion, zona, altaEn, pedidos, zonaPreferida, zonasAsignadas, co2EvitadoKg }` |
| `PUT /usuarios/{id}` | unicidad email/teléfono | `d_user`, `address` (si es ciudadano) | detalle |
| `POST /usuarios/{id}/suspender` | `d_user` (no suspenderse a sí mismo) | `status = SUSPENDIDA`; revocar sus `refresh_token` | detalle |
| `POST /usuarios/{id}/reactivar` | `d_user` | `status = ACTIVA` | detalle |
| `POST /recolectores` | unicidad email/dni/teléfono | `d_user` (`must_change_password = true`, password aleatoria con `SecureRandom`, guardada con BCrypt), `user_role = RECOLECTOR` | 201 `UsuarioResponse + { passwordProvisional }` (en claro **solo** en esta respuesta) |
| `POST /usuarios/{id}/password-provisional` | `d_user` | nueva `password_hash`, `must_change_password = true`, revocar `refresh_token` | `{ email, passwordProvisional }` |

---

## 8. Migración `V4__...sql` necesaria

- Secuencias para los códigos (`seq_collection_code`, `seq_bag_code`, `seq_incident_code`, `seq_withdrawal_code`).
- Datos base: `incident_type`, `zip_code` de Málaga, parámetros `importe_provisional`, `nave_nombre`, `nave_lat`, `nave_lng`.
- Usuario admin inicial (password BCrypt) con rol `ADMIN`.
- Opcional: `zone.active` para borrado lógico.

## 9. Orden de trabajo recomendado

1. §0 + §1 (entidad `User`, errores, JWT) — sin esto nada funciona con el front.
2. Auth y perfil (§2, §3) y catálogos (§4).
3. Admin: zonas, franjas y asignación de días (§7.5) — sin zonas/franjas el ciudadano no puede pedir.
4. Ciudadano: disponibilidad, crear/listar/cancelar pedidos (§5).
5. Recolector: recogidas, bolsas, completar (§6) → genera `settlement`.
6. Monedero y transferencias (§5 monedero, §7.4).
7. Incidencias, indicadores, impacto, ruta, preferencias y solicitudes de zona.

Cada bloque, con un test `@SpringBootTest` + `MockMvc` como los de `CollectionOrderHttpIntegrationTests`.
