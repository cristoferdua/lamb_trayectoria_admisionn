# S11 - Integración del cliente (frontend Angular con Keycloak)

*Guía única: agregar un microservicio nuevo al proyecto Lamb - Parte 11 de 16 - 2026*

!!! note "Antes de empezar: debe estar levantado"
    Desde la raíz del proyecto (`Lamb-Modulo02-Admision-Trayectoria`). Si ya está corriendo, no lo arranques otra vez.

    **Docker** (Docker Desktop abierto; se pueden pegar en la misma terminal):

    ```powershell
    docker compose -f kafka/compose-dev.yml up -d    # Kafka + Kafka UI
    docker compose -f keycloak/compose-dev.yml up -d    # Keycloak (tarda ~1 min)
    docker compose -f services/lamb-biblioteca-ms/compose-dev.yml up -d    # BD de tu servicio
    ```

    **Servicios** (cada uno en **su propia terminal**, en este orden, esperando que diga `Started ...`):

    ```powershell
    cd infra\lamb-config; .\mvnw.cmd spring-boot:run    # Config Server
    cd infra\lamb-eureka; .\mvnw.cmd spring-boot:run    # Eureka
    cd services\lamb-biblioteca-ms; .\mvnw.cmd spring-boot:run    # Tu servicio
    cd infra\lamb-gateway; .\mvnw.cmd spring-boot:run    # Gateway (al final)
    ```

    Comprueba que responden:

    - Kafka + Kafka UI: http://localhost:18090
    - Keycloak: http://localhost:18180/realms/lamb
    - BD de tu servicio: `docker ps` → `lamb-postgres-biblioteca-dev` *Up*
    - Config Server: http://localhost:18888/lamb-biblioteca-ms/dev
    - Eureka: http://localhost:18761
    - Tu servicio: http://localhost:8095/actuator/health
    - Gateway: http://localhost:18080/actuator/health

    La primera vez, antes de `npm start`: `cd clients\lamb-frontend; npm install`. El frontend se arranca **en esta sesión** (paso 4).

## 1. Introducción

Agregas páginas al frontend Angular de Lamb (`clients\lamb-frontend`), **sin diseño**: HTML simple, sin
estilos ni componentes bonitos. Siguen el patrón de `features/catalogo` de Pagatu: un **service** que llama al
Gateway y componentes con su `.ts` y su `.html`. El login de Keycloak y el envío del token **ya los hace Lamb**.

| Camino | Páginas |
|---|---|
| A - Sin CRUD | `/biblioteca`: "Hola mundo" + botón *Enviar evento a Kafka* |
| B - Con CRUD | lo del camino A + `/biblioteca/libros` (tabla), `/biblioteca/libros/nuevo` y `/biblioteca/libros/:id/editar` (formulario) |

Crea la carpeta `clients\lamb-frontend\src\app\features\biblioteca\` (cambia por tu nombre).

## 2. Código común (los dos caminos): la página Hola mundo

### 2.1 `biblioteca-service.ts`

{{lamb:clients/lamb-frontend/src/app/features/biblioteca/biblioteca-service.ts}}

!!! note "Qué cambia"
    El nombre de la clase y las rutas `/api/v1/biblioteca/...`. `environment.apiUrl` es el Gateway (`http://localhost:18080`): no lo cambies.

### 2.2 `saludo.ts`

{{lamb:clients/lamb-frontend/src/app/features/biblioteca/saludo.ts}}

### 2.3 `saludo.html`

**Camino A - Sin CRUD:** quita la última línea (el link *Ver libros*). **Camino B:** pégalo completo.

{{lamb:clients/lamb-frontend/src/app/features/biblioteca/saludo.html}}

!!! note "Camino A: el aviso de `RouterLink`"
    Si quitas el link, Angular avisa que `RouterLink` no se usa (es solo un aviso). Para quitarlo,
    borra `RouterLink` del `import` y del `imports: [...]` en `saludo.ts`.

### 2.4 La ruta: `clients\lamb-frontend\src\app\app.routes.ts`

Busca la última línea `{ path: '**', redirectTo: '' }` y **justo arriba** pega:

```ts
  // ====== BIBLIOTECA (paginas simples, sin diseño; piden login de Keycloak) ======
  {
    path: 'biblioteca',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/biblioteca/saludo').then((m) => m.Saludo)
  },
```

`adminGuard` (ya existe en Lamb) pide iniciar sesión en Keycloak. No lo cambies.

**Si elegiste el camino A, aquí terminas la S11:** ve a la sección 4 para probar.

## 3. Camino B - Con CRUD: lista y formulario

**Si quieres agregar el servicio con CRUD, sigue estos pasos.** Todos en `features\biblioteca\`.

### 3.1 `libro.model.ts`

{{lamb:clients/lamb-frontend/src/app/features/biblioteca/libro.model.ts}}

!!! note "Qué cambia"
    Un atributo por cada campo del `LibroResponse` (S1). `id?` siempre se queda. Tipos: texto → `string`,
    números → `number`, fecha → `string`, sí/no → `boolean`. Opcional → con `?`.

### 3.2 `libro-service.ts`

{{lamb:clients/lamb-frontend/src/app/features/biblioteca/libro-service.ts}}

!!! note "Qué cambia"
    Los nombres y la ruta `/api/v1/libros`. Los 5 métodos se quedan igual.

### 3.3 `libro-list.ts` (la tabla)

{{lamb:clients/lamb-frontend/src/app/features/biblioteca/libro-list.ts}}

### 3.4 `libro-list.html`

{{lamb:clients/lamb-frontend/src/app/features/biblioteca/libro-list.html}}

!!! note "Qué cambia"
    Un `<th>` y un `<td>{{ libro.x }}</td>` por cada atributo que quieras mostrar, y las rutas `/biblioteca/libros...`.
    Ajusta el `colspan="3"` al número de columnas.

### 3.5 `libro-form.ts` (agregar y editar con el mismo formulario)

Si la ruta trae `:id`, carga el libro y al guardar hace `PUT`; si no, hace `POST`.

{{lamb:clients/lamb-frontend/src/app/features/biblioteca/libro-form.ts}}

!!! note "Qué cambia"
    En `form = this.fb.nonNullable.group({...})` va **un control por atributo** con sus validaciones
    (las mismas del Request), y en `mensajeValidacion(campo: 'titulo' | 'autor')` la lista de tus atributos.
    Ejemplo para Pagos:
    ```ts
    protected readonly form = this.fb.nonNullable.group({
      monto: [0, [Validators.required, Validators.min(0)]],
      fecha: ['', [Validators.required]],
      metodoPago: ['', [Validators.maxLength(30)]],
    });
    ```

### 3.6 `libro-form.html`

{{lamb:clients/lamb-frontend/src/app/features/biblioteca/libro-form.html}}

!!! note "Qué cambia"
    Un bloque `<label>...<input formControlName="x" />...</label>` + su mensaje por cada atributo.
    El tipo de `<input>` está en la tabla de la S1 (6.1).

### 3.7 Las rutas: `app.routes.ts`

Debajo de la ruta `biblioteca` del paso 2.4, pega:

```ts
  {
    path: 'biblioteca/libros',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/biblioteca/libro-list').then((m) => m.LibroList)
  },
  {
    path: 'biblioteca/libros/nuevo',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/biblioteca/libro-form').then((m) => m.LibroForm)
  },
  {
    path: 'biblioteca/libros/:id/editar',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/biblioteca/libro-form').then((m) => m.LibroForm)
  },
```

## 4. Probar

```powershell
cd clients\lamb-frontend
npm start
```

### Camino A - Sin CRUD

1. Abre http://localhost:4200/biblioteca → te pide login → `admin` / `admin123`.
2. Si te deja en la página de inicio, vuelve a abrir http://localhost:4200/biblioteca.
3. Ves **"Hola mundo desde lamb-biblioteca-ms"**. Pulsa *Enviar evento a Kafka* → "Evento enviado a Kafka."
4. Cierra sesión, entra como `estudiante1` / `estudiante123` y pulsa el botón → "No tienes permiso para esta acción."

### Camino B - Con CRUD

1. Entra como `admin` → http://localhost:4200/biblioteca → *Ver libros*.
2. *Nuevo libro* → llena el formulario → *Guardar* → aparece en la tabla.
3. *Editar* → cambia el título → *Guardar* → se actualiza.
4. *Eliminar* → confirma → desaparece.
5. Como `estudiante1`: ve la tabla, pero al guardar sale "No tienes permiso para esta acción."

!!! note "Errores de Keycloak en la consola (F12)"
    Lamb muestra un error de CORS al cargar el perfil (`/realms/lamb/account`). Ya existía y no afecta a tus páginas.

!!! note "Archivos de esta sesión"
    **Los dos caminos** - creas en `clients\lamb-frontend\src\app\features\biblioteca\`:

    - `biblioteca-service.ts`, `saludo.ts`, `saludo.html`

    **Solo camino B** - además creas (en la misma carpeta):

    - `libro.model.ts`, `libro-service.ts`, `libro-list.ts`, `libro-list.html`, `libro-form.ts`, `libro-form.html`

    **Modificas:**

    - `clients\lamb-frontend\src\app\app.routes.ts` - 1 ruta (camino A) o 4 rutas (camino B)

## 5. Evidencia de cierre de la S11

- Capturas de las páginas funcionando (login, Hola mundo, y en el camino B: tabla, formulario, eliminar).
- Captura del `403` para `estudiante1`.

Siguiente: [S12 - Evaluación de la Unidad II](S12_Evaluacion_Unidad_2.md).
