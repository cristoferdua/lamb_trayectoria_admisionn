import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class HolaService {
  private readonly http = inject(HttpClient);

  saludo(): Observable<string> {
    return this.http.get(`${environment.apiUrl}/api/v1/hola/saludo`, { responseType: 'text' });
  }

  enviarEvento(): Observable<unknown> {
    return this.http.post(`${environment.apiUrl}/api/v1/hola/saludo/evento`, {});
  }
}
