# Fase 01 — Auditoría y fundamentos

## Objetivo

Establecer una base verificada para implementar el contrato de DevolvApp sin inventar rutas, campos, respuestas ni reglas que no estén respaldados por la fuente de verdad.

## Fuente de rutas y dependencias

- El inventario confirmado de métodos y rutas está en `web/docs/API_ENDPOINTS.md` (69 endpoints; el refresh se lista aparte).
- Ese inventario no define payloads, parámetros de consulta, permisos, validaciones, códigos HTTP ni cuerpos de respuesta. Confirmar la fuente funcional de esos detalles con el equipo antes de implementar cada endpoint; registrar como bloqueo lo que siga sin especificación.
- No resolver silenciosamente discrepancias entre el contrato, `V1__create_tables.sql` y el código.

## Alcance y actividades

1. Confirmar el contrato de API: rutas, métodos, DTO, validaciones, roles, estados, códigos HTTP y respuestas.
2. Comparar el esquema único disponible, `src/main/resources/db/migration/V1__create_tables.sql`, con las entidades, repositorios y consultas SQL existentes.
3. Auditar las capas actuales (`controller`, `service`, `repository`, `entity`, `dto`, `mappers`) y distinguir comportamiento implementado, parcial y pendiente.
4. Auditar manejo de errores (`ApiExceptionHandler` y excepciones), seguridad, autenticación/autorización, configuración CORS y pruebas existentes.
5. Registrar riesgos, contradicciones y decisiones pendientes con evidencia y componente afectado.

## Endpoints dentro de la auditoría

La fase audita el inventario completo **#1–69**, sin implementar esos endpoints. Comprobar especialmente que el código actual solo contiene algunas rutas antiguas/parciales y que la existencia de una ruta en el inventario no implica que su contrato de entrada/salida esté definido. `POST /api/auth/refresh` es el endpoint adicional **#70**, fuera del alcance contractual hasta que se apruebe.

## Reglas de ejecución

- `V1__create_tables.sql` es la referencia de persistencia. No agregar migraciones adicionales salvo petición expresa.
- Proteger los cambios preexistentes del árbol de trabajo; no revertirlos como parte de esta fase.
- No incluir contraseñas, tokens ni otros secretos en documentación, logs o fixtures.

## Verificación y aceptación

- Existe una matriz de contrato/esquema/código para las funcionalidades previstas.
- Las discrepancias y los requisitos no verificables están documentados; no se han resuelto por suposición.
- Está confirmado el inventario de métodos/rutas; la fuente de los detalles funcionales queda confirmada o se declaran los bloqueos concretos por endpoint/fase.
- Se identifican las pruebas existentes y las brechas de cobertura relevantes.

## Salida

Una línea base aprobada para comenzar las fases funcionales y una lista explícita de bloqueos/riesgos que debe acompañarlas.
