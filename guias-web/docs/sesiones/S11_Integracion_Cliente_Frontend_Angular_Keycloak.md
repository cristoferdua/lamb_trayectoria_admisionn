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

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class BibliotecaService {
  private readonly http = inject(HttpClient);

  saludo(): Observable<string> {
    return this.http.get(`${environment.apiUrl}/api/v1/biblioteca/saludo`, { responseType: 'text' });
  }

  enviarEvento(): Observable<unknown> {
    return this.http.post(`${environment.apiUrl}/api/v1/biblioteca/saludo/evento`, {});
  }
}
```

!!! note "Qué cambia"
    El nombre de la clase y las rutas `/api/v1/biblioteca/...`. `environment.apiUrl` es el Gateway (`http://localhost:18080`): no lo cambies.

### 2.2 `saludo.ts`

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { BibliotecaService } from './biblioteca-service';

@Component({
  selector: 'app-saludo',
  imports: [RouterLink],
  templateUrl: './saludo.html',
})
export class Saludo implements OnInit {
  private readonly bibliotecaService = inject(BibliotecaService);
  protected readonly mensaje = signal('Cargando...');
  protected readonly resultado = signal<string | null>(null);

  ngOnInit(): void {
    this.bibliotecaService.saludo().subscribe({
      next: (texto) => this.mensaje.set(texto),
      error: () => this.mensaje.set('No se pudo conectar con el servicio.'),
    });
  }

  enviarEvento(): void {
    this.bibliotecaService.enviarEvento().subscribe({
      next: () => this.resultado.set('Evento enviado a Kafka.'),
      error: (err: HttpErrorResponse) =>
        this.resultado.set(err.status === 403 ? 'No tienes permiso para esta acción.' : 'No se pudo enviar el evento.'),
    });
  }
}
```

### 2.3 `saludo.html`

**Camino A - Sin CRUD:** quita la última línea (el link *Ver libros*). **Camino B:** pégalo completo.

```html
<h1>{{ mensaje() }}</h1>

<button (click)="enviarEvento()">Enviar evento a Kafka</button>
@if (resultado()) {
  <p>{{ resultado() }}</p>
}

<p><a routerLink="/biblioteca/libros">Ver libros</a></p>
```

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

```ts
export interface Libro {
  id?: number;
  titulo: string;
  autor?: string;
}
```

!!! note "Qué cambia"
    Un atributo por cada campo del `LibroResponse` (S1). `id?` siempre se queda. Tipos: texto → `string`,
    números → `number`, fecha → `string`, sí/no → `boolean`. Opcional → con `?`.

### 3.2 `libro-service.ts`

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Libro } from './libro.model';

@Injectable({ providedIn: 'root' })
export class LibroService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Libro[]> {
    return this.http.get<Libro[]>(`${environment.apiUrl}/api/v1/libros`);
  }

  obtener(id: number): Observable<Libro> {
    return this.http.get<Libro>(`${environment.apiUrl}/api/v1/libros/${id}`);
  }

  crear(libro: Libro): Observable<Libro> {
    return this.http.post<Libro>(`${environment.apiUrl}/api/v1/libros`, libro);
  }

  actualizar(id: number, libro: Libro): Observable<Libro> {
    return this.http.put<Libro>(`${environment.apiUrl}/api/v1/libros/${id}`, libro);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/api/v1/libros/${id}`);
  }
}
```

!!! note "Qué cambia"
    Los nombres y la ruta `/api/v1/libros`. Los 5 métodos se quedan igual.

### 3.3 `libro-list.ts` (la tabla)

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { LibroService } from './libro-service';
import { Libro } from './libro.model';

@Component({
  selector: 'app-libro-list',
  imports: [RouterLink],
  templateUrl: './libro-list.html',
})
export class LibroList implements OnInit {
  private readonly libroService = inject(LibroService);
  protected readonly libros = signal<Libro[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly errorAccion = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.libroService.listar().subscribe({
      next: (data) => {
        this.libros.set(data);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar la lista de libros.');
        this.cargando.set(false);
      },
    });
  }

  eliminar(id: number): void {
    if (!confirm(`¿Seguro que quieres eliminar el libro ${id}?`)) {
      return;
    }
    this.errorAccion.set(null);
    this.libroService.eliminar(id).subscribe({
      next: () => this.cargar(),
      error: (err: HttpErrorResponse) => {
        this.errorAccion.set(
          err.status === 403 ? 'No tienes permiso para esta acción.' : 'No se pudo eliminar el libro.',
        );
      },
    });
  }
}
```

### 3.4 `libro-list.html`

```html
<h1>Libros</h1>

@if (cargando()) {
  <p>Cargando libros...</p>
} @else if (error()) {
  <p>{{ error() }}</p>
} @else {
  <a routerLink="/biblioteca/libros/nuevo">Nuevo libro</a>

  @if (errorAccion()) {
    <p class="error">{{ errorAccion() }}</p>
  }

  <table>
    <thead>
      <tr><th>Titulo</th><th>Autor</th><th></th></tr>
    </thead>
    <tbody>
      @for (libro of libros(); track libro.id) {
        <tr>
          <td>{{ libro.titulo }}</td>
          <td>{{ libro.autor }}</td>
          <td>
            <a [routerLink]="['/biblioteca/libros', libro.id, 'editar']">Editar</a>
            <button (click)="eliminar(libro.id!)">Eliminar</button>
          </td>
        </tr>
      } @empty {
        <tr><td colspan="3">No hay libros registrados.</td></tr>
      }
    </tbody>
  </table>
}
```

!!! note "Qué cambia"
    Un `<th>` y un `<td>{{ libro.x }}</td>` por cada atributo que quieras mostrar, y las rutas `/biblioteca/libros...`.
    Ajusta el `colspan="3"` al número de columnas.

### 3.5 `libro-form.ts` (agregar y editar con el mismo formulario)

Si la ruta trae `:id`, carga el libro y al guardar hace `PUT`; si no, hace `POST`.

```ts
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LibroService } from './libro-service';

@Component({
  selector: 'app-libro-form',
  imports: [ReactiveFormsModule],
  templateUrl: './libro-form.html',
})
export class LibroForm {
  private readonly fb = inject(FormBuilder);
  private readonly libroService = inject(LibroService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly id = signal<number | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.maxLength(150)]],
    autor: ['', [Validators.maxLength(100)]],
  });

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.id.set(id);
      this.cargando.set(true);
      this.libroService.obtener(id).subscribe({
        next: (libro) => {
          this.form.patchValue(libro);
          this.cargando.set(false);
        },
        error: () => {
          this.error.set('No se pudo cargar el libro.');
          this.cargando.set(false);
        },
      });
    }
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const valor = this.form.getRawValue();
    const id = this.id();
    const peticion = id ? this.libroService.actualizar(id, valor) : this.libroService.crear(valor);

    peticion.subscribe({
      next: () => this.router.navigate(['/biblioteca/libros']),
      error: (err: HttpErrorResponse) => {
        this.error.set(
          err.status === 403 ? 'No tienes permiso para esta acción.' : 'No se pudo guardar el libro.',
        );
      },
    });
  }

  cancelar(): void {
    this.router.navigate(['/biblioteca/libros']);
  }

  protected mensajeValidacion(campo: 'titulo' | 'autor'): string {
    const control = this.form.controls[campo];

    if (!control.touched) return '';
    if (control.hasError('required')) return 'Este campo es obligatorio.';
    if (control.hasError('maxlength')) {
      return `Máximo ${control.getError('maxlength').requiredLength} caracteres.`;
    }
    return '';
  }
}
```

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

```html
<h1>{{ id() ? 'Editar libro' : 'Nuevo libro' }}</h1>

<form [formGroup]="form" (ngSubmit)="guardar()">
  @if (cargando()) {
    <p>Cargando...</p>
  }

  <label>
    Titulo
    <input type="text" formControlName="titulo" />
  </label>
  @if (mensajeValidacion('titulo'); as mensaje) {
    <p class="error">{{ mensaje }}</p>
  }

  <label>
    Autor
    <input type="text" formControlName="autor" />
  </label>
  @if (mensajeValidacion('autor'); as mensaje) {
    <p class="error">{{ mensaje }}</p>
  }

  @if (error()) {
    <p class="error">{{ error() }}</p>
  }

  <button type="submit" [disabled]="cargando()">Guardar</button>
  <button type="button" (click)="cancelar()">Cancelar</button>
</form>
```

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
