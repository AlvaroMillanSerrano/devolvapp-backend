# Fase 01 — Auditoría y fundamentos

## Objetivo

Establecer una base verificada para implementar el contrato de DevolvApp sin inventar rutas, campos, respuestas ni reglas que no estén respaldados por la fuente de verdad.

## Dependencias y bloqueos

- Fuente inicial: `proyecto.md`.
- El contrato al que remite (`web/docs/API_ENDPOINTS.md`) no se encontró en el estado actual del repositorio. Localizarlo, recuperarlo o confirmar con el equipo dónde reside antes de definir detalles de endpoints. Si no está disponible, registrar explícitamente el bloqueo.
- No resolver silenciosamente discrepancias entre el contrato, `V1__create_tables.sql` y el código.

## Alcance y actividades

1. Confirmar el contrato de API: rutas, métodos, DTO, validaciones, roles, estados, códigos HTTP y respuestas.
2. Comparar el esquema único disponible, `src/main/resources/db/migration/V1__create_tables.sql`, con las entidades, repositorios y consultas SQL existentes.
3. Auditar las capas actuales (`controller`, `service`, `repository`, `entity`, `dto`, `mappers`) y distinguir comportamiento implementado, parcial y pendiente.
4. Auditar manejo de errores (`ApiExceptionHandler` y excepciones), seguridad, autenticación/autorización, configuración CORS y pruebas existentes.
5. Registrar riesgos, contradicciones y decisiones pendientes con evidencia y componente afectado.

## Reglas de ejecución

- `V1__create_tables.sql` es la referencia de persistencia. No agregar migraciones adicionales salvo petición expresa.
- Proteger los cambios preexistentes del árbol de trabajo; no revertirlos como parte de esta fase.
- No incluir contraseñas, tokens ni otros secretos en documentación, logs o fixtures.

## Verificación y aceptación

- Existe una matriz de contrato/esquema/código para las funcionalidades previstas.
- Las discrepancias y los requisitos no verificables están documentados; no se han resuelto por suposición.
- Está confirmada la disponibilidad y versión del contrato, o queda declarado el bloqueo para las fases afectadas.
- Se identifican las pruebas existentes y las brechas de cobertura relevantes.

## Salida

Una línea base aprobada para comenzar las fases funcionales y una lista explícita de bloqueos/riesgos que debe acompañarlas.
