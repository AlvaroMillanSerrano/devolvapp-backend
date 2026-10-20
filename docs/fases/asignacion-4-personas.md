# Asignación de las 9 fases entre Dani, Jorge, Javier y Álvaro

Los documentos originales `01`–`09` se mantienen como especificación funcional. Este archivo asigna quién implementa cada fase y define propietarios únicos para archivos y áreas compartidas.

## Reparto

| Persona | Fases | Responsabilidad principal |
|---|---|---|
| **Dani** | 01 y 02 | Auditoría y fundamentos; autenticación, seguridad, perfil y usuarios |
| **Jorge** | 03 y 04 | Catálogos, parámetros, zonas, CP, franjas y asignaciones admin |
| **Javier** | 05 y 07 | Pedidos y disponibilidad de ciudadano; monedero y retiradas |
| **Álvaro** | 06 y 08 | Operación del recolector, bolsas, incidencias, liquidación y operación/impacto |
| **Todos, coordinados por Álvaro** | 09 | Pruebas de integración y cierre; cada persona valida su módulo, Dani coordina la suite y el informe final |

## Propiedad exclusiva para evitar conflictos

- **Dani:** `SecurityConfig`, JWT/filtro/principal, auth, perfil, `UserService`, `UserController`, `UserMapper`, DTO de usuario/auth, pruebas de esos flujos y sus cambios en manejo de errores comunes.
- **Jorge:** paquetes propios de catálogo, parámetros, zona y planificación admin: controladores, servicios, DTO, repositorios y pruebas de esos recursos.
- **Javier:** `CollectionOrderController`, `CollectionOrderService`, DTO ciudadanos de pedidos y paquetes de monedero/retiradas. Es el único que edita `CollectionOrder`, `Bag`, `BagContent` y sus repositorios compartidos cuando haga falta para el pedido.
- **Álvaro:** controladores/servicios/DTO nuevos del recolector, incidencias, historial y settlement/liquidación, más pruebas de esos flujos. No modifica los archivos de creación/consulta del pedido propiedad de Javier.
- **Álvaros, como integrador:** cambios de configuración transversal (principal, errores comunes, paginación o autorización) los realiza él en un PR dedicado. Los demás solicitan cambios mediante interfaz acordada y no editan esos archivos en paralelo.
- **Esquema:** nadie altera `V1__create_tables.sql` ni agrega migraciones sin acuerdo expreso. La estructura actual es la referencia.

Si una tarea necesita un archivo propiedad de otra persona, se acuerda un único dueño de edición para ese cambio y se integra en un PR separado. No se resuelve editando el mismo archivo en dos ramas a la vez.

## División de la fase 08

Para que fase 08 tenga un solo dueño por recurso:

- **Dani:** gestión administrativa de usuarios/recolectores, suspensión y revocación de sesiones.
- **Jorge:** administración de zonas, CP, franjas, días y asignaciones.
- **Javier:** impacto, monedero y consultas propias del ciudadano.
- **Álvaro:** ruta, historial, preferencias e impacto propio del recolector, además de incidencias/operación.
- **Dani coordina indicadores generales** y asigna cada endpoint al dueño del paquete de datos que lo soporta antes de empezar su implementación.

Cada persona modifica solamente sus controladores/servicios/DTO y pruebas. Si una ruta admin cruza paquetes, se separa por recurso y se acuerda el contrato antes de codificar.

## Secuencia para trabajar a la vez

### Arranque paralelo

1. **Dani (fase 01)** confirma/localiza el contrato API, coteja `V1` con el código y publica las interfaces comunes de seguridad, errores y paginación.
2. **Jorge, Javier y Álvaro** auditan en paralelo sus propios dominios y preparan pruebas, sin editar archivos ajenos ni asumir rutas/JSON.
3. Al confirmar el contrato y congelar interfaces, los cuatro comienzan sus fases asignadas en ramas/PR separados.

El contrato `web/docs/API_ENDPOINTS.md` se menciona en `proyecto.md`, pero no está en el checkout actual. No implementar endpoints concretos hasta localizarlo o recibir confirmación de la fuente correcta.

### Dependencias durante la implementación

- **Fases 02, 03 y la preparación de 05/06** avanzan en paralelo una vez confirmados contrato e interfaces.
- **Javier (05) y Álvaro (06)** pueden implementar simultáneamente en clases separadas. Javier es dueño del modelo y la persistencia de pedido/bolsa; Álvaro consume ese modelo para operar la recogida.
- Antes de que Álvaro integre la finalización, ambos acuerdan la interfaz estable del estado de bolsa; cada archivo de modelo compartido conserva un solo editor.
- **Javier (07)** puede preparar DTOs/pruebas mientras trabaja Álvaro, pero el cálculo de saldo se integra cuando Álvaro publique el contrato de settlement/liquidación.
- **Fase 09** empieza cuando hay módulos integrados: cada persona corrige su paquete y pruebas; Dani coordina ejecución y cierre sin apropiarse de cambios de otros módulos.

## Reglas de rama e integración

- Usar una rama y PR por persona/fase, con título que identifique fases y responsable.
- Limitar cada PR a archivos del propietario indicado; listar cualquier excepción antes de cambiarla.
- No mezclar refactors ajenos, cambios de esquema no autorizados ni cambios de contrato sin avisar a los consumidores.
- Publicar DTOs/interfaces compartidos antes de que otros módulos dependan de ellos.
- Cada PR incluye pruebas focalizadas realmente ejecutadas, archivos afectados y bloqueos.
- Integrar cambios pequeños; resolver conflictos junto al propietario del archivo, nunca descartando cambios de otro compañero.

## Resumen rápido

```text
Dani:   01 Auditoría + 02 Auth/perfil + gestión admin de usuarios en 08
Jorge:  03 Catálogos + 04 Zonas/planificación + administración de zonas en 08
Javier: 05 Pedidos ciudadano + 07 Monedero + impacto ciudadano en 08
Álvaro: 06 Operación/liquidación + operación e impacto recolector en 08
Todos:  09 Integración; Dani coordina la ejecución y el cierre
```
