import { Injectable, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Observable, catchError, map, of, shareReplay, tap } from 'rxjs';
import { Session } from '../../../auth/session';
import { FarmaciaApi } from '../../farmacia-api';

// true cuando Farmacia ya guardó su inventario inicial: la opción se quita del menú (farmacia-nav.ts).
export const inventarioInicialCerrado = signal(false);

// Pregunta una sola vez al servidor si el inventario inicial de Farmacia ya se guardó.
@Injectable({ providedIn: 'root' })
export class InventarioInicialEstado {
  private readonly api = inject(FarmaciaApi);
  private consulta: Observable<boolean> | null = null;

  cerrado(): Observable<boolean> {
    this.consulta ??= this.api.conteoInicial().pipe(
      map((estado) => estado.cerrado),
      tap((cerrado) => inventarioInicialCerrado.set(cerrado)),
      // Si falla, se deja la opción: el backend de todos modos rechaza con 409 si ya se guardó.
      catchError(() => {
        this.consulta = null;
        return of(false);
      }),
      shareReplay(1),
    );
    return this.consulta;
  }

  marcarCerrado(): void {
    inventarioInicialCerrado.set(true);
    this.consulta = of(true);
  }
}

// En todas las pantallas de Farmacia: consulta en segundo plano para ocultar la opción del menú.
export const consultarInventarioInicial: CanActivateFn = () => {
  if (inject(Session).usuarioActual()?.rol === 'FARMACIA') {
    inject(InventarioInicialEstado).cerrado().subscribe();
  }
  return true;
};

// En la pantalla del inventario inicial: si ya se guardó, entrar escribiendo la dirección manda a Existencias.
export const inventarioInicialAbierto: CanActivateFn = () => {
  const router = inject(Router);
  return inject(InventarioInicialEstado)
    .cerrado()
    .pipe(map((cerrado) => (cerrado ? router.parseUrl('/farmacia/inventario/existencias') : true)));
};
