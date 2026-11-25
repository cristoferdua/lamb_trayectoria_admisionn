import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { HolaService } from './hola-service';

@Component({
  selector: 'app-saludo',
  imports: [],
  templateUrl: './saludo.html',
})
export class Saludo implements OnInit {
  private readonly holaService = inject(HolaService);
  protected readonly mensaje = signal('Cargando...');
  protected readonly resultado = signal<string | null>(null);

  ngOnInit(): void {
    this.holaService.saludo().subscribe({
      next: (texto) => this.mensaje.set(texto),
      error: () => this.mensaje.set('No se pudo conectar con el servicio.'),
    });
  }

  enviarEvento(): void {
    this.holaService.enviarEvento().subscribe({
      next: () => this.resultado.set('Evento enviado a Kafka.'),
      error: (err: HttpErrorResponse) =>
        this.resultado.set(err.status === 403 ? 'No tienes permiso para esta acción.' : 'No se pudo enviar el evento.'),
    });
  }
}
