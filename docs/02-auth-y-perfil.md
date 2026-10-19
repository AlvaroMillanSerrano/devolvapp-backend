# Fase 02 — Auth y perfil

## Objetivo

Implementar los flujos de autenticación y gestión de perfil conforme al contrato confirmado, protegiendo la identidad y las credenciales del usuario.

## Dependencias

- Fase 01 completada; contrato API y discrepancias relevantes confirmados.
- Revisar `V1__create_tables.sql`, especialmente `d_user`, `user_role`, `address`, `refresh_token` y `password_reset_token`.

## Alcance

### Endpoints asignados a esta fase

| # | Método | Endpoint |
|---:|---|---|
| 1 | POST | `/api/auth/login` |
| 2 | POST | `/api/auth/logout` |
| 3 | GET | `/api/auth/me` |
| 4 | POST | `/api/auth/registro` |
| 5 | POST | `/api/auth/password/recuperar` |
| 6 | GET | `/api/auth/password/token/{token}` |
| 7 | POST | `/api/auth/password/restablecer` |
| 8 | GET | `/api/perfil` |
| 9 | PUT | `/api/perfil` |
| 10 | PUT | `/api/perfil/password` |
| 11 | PUT | `/api/perfil/password-inicial` |

La gestión administrativa de usuarios y recolectores (**#63–69**) pertenece a la fase 08, aunque reutilice los servicios de usuario y seguridad establecidos aquí.

El inventario de rutas no especifica cuerpos, respuestas, parámetros ni permisos detallados. Confirmar esos contratos antes de implementar cada operación; no añadir `POST /api/auth/refresh` sin aprobación y definición formal.

- Auth `/api/auth`: login, logout, consulta de usuario autenticado, registro, recuperación y restablecimiento de contraseña; no incorporar `POST /api/auth/refresh` sin documentarlo y coordinarlo con el frontend.
- Perfil `/api/perfil`: consulta/actualización de datos y dirección, cambio de contraseña y contraseña inicial, según contrato.
- JWT stateless, principal `AuthUser` (`id`, `email`, `roles`), filtro y autorización por roles.
- BCrypt; unicidad de email/DNI/teléfono; asignación segura del rol `CIUDADANO`.
- Tokens de reset/refresh almacenados como hashes, con vencimiento, uso único y revocación cuando corresponda.
- Intentos fallidos con Redis si está configurado y respuestas de recuperación que no revelen si el email existe.
- Obtención de identidad mediante el principal autenticado, nunca desde un ID confiado enviado por el cliente.

## Componentes a revisar

Contrato API; `SecurityConfig`; `User`, `Role`, `UserRepository`, `UserService`, `UserController`, DTO y `UserMapper`; configuración JWT/Redis; manejo de errores y pruebas.

## Pruebas y aceptación

- Cubrir registro y login correctos; validación y duplicados; credenciales incorrectas, usuario suspendido e intentos fallidos; autorización por rol; recuperación con email existente/no existente; token expirado/usado; edición de perfil y dirección.
- Comprobar que no se acepta autoasignación de roles privilegiados ni se exponen hashes, tokens o credenciales.
- Verificar revocación de sesiones donde lo exige el contrato y que errores y campos coincidan con el formato común.
- Confirmar que cambiar la dirección actual no altera las instantáneas históricas de pedidos.
