# Listado de endpoints de la API

> **Alcance:** este documento registra los métodos y rutas confirmados. No define payloads, parámetros de consulta, permisos, validaciones, códigos HTTP ni esquemas de respuesta; esos detalles deben acordarse antes de implementar cada endpoint.
>
> El contrato documenta 69 endpoints. Además, se identifica `POST /api/auth/refresh` como endpoint adicional pendiente de incorporar formalmente al contrato.

## Índice

1. [Autenticación](#1-autenticación)
2. [Perfil](#2-perfil)
3. [Configuración y catálogo](#3-configuración-y-catálogo)
4. [Ciudadano](#4-ciudadano)
5. [Recolector](#5-recolector)
6. [Administrador](#6-administrador)
7. [Endpoint adicional fuera del contrato](#7-endpoint-adicional-fuera-del-contrato)

## 1. Autenticación

**Prefijo:** `/api/auth`

| # | Método | Endpoint |
|---:|---|---|
| 1 | POST | `/api/auth/login` |
| 2 | POST | `/api/auth/logout` |
| 3 | GET | `/api/auth/me` |
| 4 | POST | `/api/auth/registro` |
| 5 | POST | `/api/auth/password/recuperar` |
| 6 | GET | `/api/auth/password/token/{token}` |
| 7 | POST | `/api/auth/password/restablecer` |

## 2. Perfil

**Prefijo:** `/api/perfil`

| # | Método | Endpoint |
|---:|---|---|
| 8 | GET | `/api/perfil` |
| 9 | PUT | `/api/perfil` |
| 10 | PUT | `/api/perfil/password` |
| 11 | PUT | `/api/perfil/password-inicial` |

## 3. Configuración y catálogo

| # | Método | Endpoint |
|---:|---|---|
| 12 | GET | `/api/config/parametros` |
| 13 | GET | `/api/catalogo/tipos-envase` |
| 14 | GET | `/api/catalogo/tipos-incidencia` |
| 15 | GET | `/api/catalogo/codigos-postales` |

## 4. Ciudadano

**Prefijo:** `/api/ciudadano`

### 4.1. Pedidos y recogidas

| # | Método | Endpoint |
|---:|---|---|
| 16 | GET | `/api/ciudadano/pedidos` |
| 17 | GET | `/api/ciudadano/pedidos/{id}` |
| 18 | GET | `/api/ciudadano/disponibilidad` |
| 19 | POST | `/api/ciudadano/pedidos` |
| 20 | POST | `/api/ciudadano/pedidos/{id}/cancelar` |

### 4.2. Impacto

| # | Método | Endpoint |
|---:|---|---|
| 21 | GET | `/api/ciudadano/impacto` |

### 4.3. Monedero

| # | Método | Endpoint |
|---:|---|---|
| 22 | GET | `/api/ciudadano/monedero` |
| 23 | GET | `/api/ciudadano/monedero/movimientos` |
| 24 | POST | `/api/ciudadano/monedero/retiradas` |

## 5. Recolector

**Prefijo:** `/api/recolector`

### 5.1. Recogidas

| # | Método | Endpoint |
|---:|---|---|
| 25 | GET | `/api/recolector/recogidas` |
| 26 | GET | `/api/recolector/recogidas/{id}` |
| 27 | POST | `/api/recolector/recogidas/{id}/iniciar` |
| 28 | PUT | `/api/recolector/recogidas/{id}/bolsas/{bolsaId}` |
| 29 | POST | `/api/recolector/recogidas/{id}/completar` |
| 30 | POST | `/api/recolector/recogidas/{id}/incidencias` |

### 5.2. Ruta e historial

| # | Método | Endpoint |
|---:|---|---|
| 31 | GET | `/api/recolector/ruta` |
| 32 | GET | `/api/recolector/historial` |
| 33 | GET | `/api/recolector/impacto` |

### 5.3. Preferencias y solicitudes de zona

| # | Método | Endpoint |
|---:|---|---|
| 34 | GET | `/api/recolector/preferencias` |
| 35 | PUT | `/api/recolector/preferencias/zona` |
| 36 | POST | `/api/recolector/solicitudes-zona` |
| 37 | POST | `/api/recolector/solicitudes-zona/{id}/cancelar` |

## 6. Administrador

**Prefijo:** `/api/admin`

### 6.1. Panel e indicadores

| # | Método | Endpoint |
|---:|---|---|
| 38 | GET | `/api/admin/pendientes` |
| 39 | GET | `/api/admin/indicadores` |

### 6.2. Incidencias

| # | Método | Endpoint |
|---:|---|---|
| 40 | GET | `/api/admin/incidencias` |
| 41 | GET | `/api/admin/incidencias/{id}` |
| 42 | PATCH | `/api/admin/incidencias/{id}` |

### 6.3. Recogidas

| # | Método | Endpoint |
|---:|---|---|
| 43 | GET | `/api/admin/recogidas` |
| 44 | GET | `/api/admin/recogidas/{id}` |
| 45 | PUT | `/api/admin/recogidas/{id}/recolector` |
| 46 | POST | `/api/admin/recogidas/{id}/cancelar` |

### 6.4. Transferencias

| # | Método | Endpoint |
|---:|---|---|
| 47 | GET | `/api/admin/transferencias` |
| 48 | GET | `/api/admin/transferencias/{id}` |
| 49 | POST | `/api/admin/transferencias/{id}/resultado` |
| 50 | POST | `/api/admin/transferencias/pagar-lote` |

### 6.5. Códigos postales, zonas y recolectores

| # | Método | Endpoint |
|---:|---|---|
| 51 | GET | `/api/admin/codigos-postales` |
| 52 | GET | `/api/admin/zonas` |
| 53 | GET | `/api/admin/recolectores` |
| 54 | POST | `/api/admin/zonas` |
| 55 | PUT | `/api/admin/zonas/{id}` |
| 56 | DELETE | `/api/admin/zonas/{id}` |
| 57 | PUT | `/api/admin/zonas/{id}/dias/{diaSemana}/recolector` |
| 58 | POST | `/api/admin/zonas/{id}/franjas` |
| 59 | PUT | `/api/admin/franjas/{id}` |
| 60 | DELETE | `/api/admin/franjas/{id}` |

### 6.6. Solicitudes de cambio de zona

| # | Método | Endpoint |
|---:|---|---|
| 61 | GET | `/api/admin/solicitudes-zona` |
| 62 | POST | `/api/admin/solicitudes-zona/{id}/resolver` |

### 6.7. Usuarios

| # | Método | Endpoint |
|---:|---|---|
| 63 | GET | `/api/admin/usuarios` |
| 64 | GET | `/api/admin/usuarios/{id}` |
| 65 | PUT | `/api/admin/usuarios/{id}` |
| 66 | PUT | `/api/admin/usuarios/{id}/roles` |
| 67 | POST | `/api/admin/usuarios/{id}/suspender` |
| 68 | POST | `/api/admin/usuarios/{id}/reactivar` |
| 69 | POST | `/api/admin/recolectores` |
| 70 | POST | `/api/admin/usuarios/{id}/password-provisional` |

## 7. Endpoint adicional fuera del contrato

| # | Estado | Método | Endpoint |
|---:|---|---|---|
| 71 | Adicional; pendiente de incorporar al contrato | POST | `/api/auth/refresh` |

Su comportamiento, autenticación, parámetros y respuestas están pendientes de definición y aprobación antes de implementarlo.

## Resumen

| Concepto | Cantidad |
|---|---:|
| Endpoints documentados | 70 |
| Endpoint adicional identificado | 1 |
| Total del listado | 71 |
