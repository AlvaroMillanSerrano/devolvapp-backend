# Asignación de las 9 fases entre Dani, Jorge, Javier y Álvaro

Los documentos originales `01`–`09` se mantienen como especificación funcional. Este archivo asigna quién implementa cada fase y define propietarios únicos para archivos y áreas compartidas.

## Reparto

| Persona | Fases | Endpoints y responsabilidad principal |
|---|---|---|
| **Dani** | 01 y 02; parte de 08 | Auditoría; endpoints **1–11** (auth/perfil) y **63–69** (usuarios/recolectores admin); listado de recolectores **53**; coordina el desglose de indicadores **38–39**. |
| **Jorge** | 03 y 04; parte de 08 | Catálogos y parámetros **12–15**; códigos postales/zonas/franjas/asignaciones **51–52, 54–60**; solicitudes de cambio de zona admin **61–62**. |
| **Javier** | 05 y 07; parte de 08 | Pedidos/disponibilidad **16–20**; monedero ciudadano **22–24**; impacto ciudadano **21**; administración de recogidas **43–46**; transferencias **47–50**. |
| **Álvaro** | 06 y parte de 08 | Operación del recolector **25–30**; ruta, historial, impacto, preferencias y solicitudes de zona **31–37**; administración de incidencias **40–42**. |
| **Todos; Dani coordina** | 09 | Integración y cierre de los endpoints **1–69**; cada persona valida su módulo. El endpoint adicional **70** queda fuera salvo aprobación expresa. |

Los endpoints **38–39** (pendientes e indicadores) son transversales: Dani coordina su desglose y, antes de implementarlos, acuerda con el equipo qué propietario aporta cada dato y quién mantiene cada controlador/servicio. No se presuponen contratos de respuesta que no estén definidos.

## Propiedad exclusiva para evitar conflictos

- **Dani:** `SecurityConfig`, JWT/filtro/principal, auth, perfil, `UserService`, `UserController`, `UserMapper`, DTO de usuario/auth y pruebas de esos flujos. Propone los errores que necesita Auth y acuerda cambios comunes con Álvaro.
- **Jorge:** paquetes propios de catálogo, parámetros, zona y planificación admin: controladores, servicios, DTO, repositorios y pruebas de esos recursos.
- **Javier:** `CollectionOrderController`, `CollectionOrderService`, DTO ciudadanos de pedidos y paquetes de monedero/retiradas. Es el único que edita `CollectionOrder`, `Bag`, `BagContent` y sus repositorios compartidos cuando haga falta para el pedido.
- **Álvaro:** controladores/servicios/DTO nuevos del recolector, incidencias, historial y settlement/liquidación, más pruebas de esos flujos. No modifica los archivos de creación/consulta del pedido propiedad de Javier.
- **Álvaro, como integrador:** es el único editor de los componentes transversales compartidos de errores y paginación, y de cambios transversales posteriores en autorización/principal. Dani implementa la base JWT/Auth de fase 02; cualquier extensión transversal posterior se acuerda y se integra en un PR dedicado, sin ediciones paralelas.
- **Esquema:** nadie altera `V1__create_tables.sql` ni agrega migraciones sin acuerdo expreso. La estructura actual es la referencia.

Si una tarea necesita un archivo propiedad de otra persona, se acuerda un único dueño de edición para ese cambio y se integra en un PR separado. No se resuelve editando el mismo archivo en dos ramas a la vez.

## División de la fase 08

Para que fase 08 tenga un solo dueño por recurso:

- **Dani:** endpoints 63–69 y listado de recolectores 53.
- **Jorge:** endpoints 61–62.
- **Javier:** impacto ciudadano 21 y endpoints administrativos de recogidas 43–46; consulta con Jorge las reglas de asignación de recolector del endpoint 45.
- **Álvaro:** endpoints 31–37 y 40–42.
- **Dani coordina los endpoints 38–39** y asigna cada parte al dueño del paquete de datos que la soporta antes de empezar su implementación.

Cada persona modifica solamente sus controladores/servicios/DTO y pruebas. Si una ruta admin cruza paquetes, se separa por recurso y se acuerda el contrato antes de codificar.

## Secuencia para trabajar a la vez

### Arranque paralelo

1. **Dani (fase 01)** coteja el inventario de rutas confirmado en `web/docs/API_ENDPOINTS.md` con `V1` y el código; confirma con el equipo la fuente de verdad para payloads, parámetros, permisos, validaciones y respuestas; documenta las interfaces comunes acordadas. Dani implementa la base de seguridad/Auth; Álvaro integra los componentes comunes de errores y paginación.
2. **Jorge, Javier y Álvaro** auditan en paralelo sus propios dominios y preparan pruebas, sin editar archivos ajenos ni asumir rutas/JSON.
3. Al confirmar para cada bloque los detalles funcionales necesarios y congelar sus interfaces, los cuatro comienzan el trabajo asignado en ramas/PR separados.

`web/docs/API_ENDPOINTS.md` ya contiene los 69 métodos y rutas. Aún no especifica payloads, parámetros de consulta, permisos, validaciones, códigos HTTP ni cuerpos de respuesta. Confirmar esos detalles con la fuente funcional/equipo antes de implementar el endpoint afectado; no inventarlos.

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
Dani:   01 Auditoría + 02 (#1–11) + 08 (#53, #63–69; coordina #38–39)
Jorge:  03 (#12–15) + 04 (#51–52, #54–60) + 08 (#61–62)
Javier: 05 (#16–20) + 07 (#22–24, #47–50) + 08 (#21, #43–46)
Álvaro: 06 (#25–30) + 08 (#31–37, #40–42)
Todos:  09 Integración de #1–69; #70 solo si se aprueba y define
```
