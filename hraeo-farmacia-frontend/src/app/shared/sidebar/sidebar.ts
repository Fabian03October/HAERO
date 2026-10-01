import { Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { IsActiveMatchOptions, NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { MenuMovil } from '../menu-movil';

export interface ItemMenu {
  etiqueta: string;
  routerLink?: string;
  etiquetaExtra?: string;
  // Si tiene hijos se muestra como submenú desplegable; routerLink es el prefijo
  // de ruta del grupo y sirve para abrirlo cuando alguna de sus páginas está activa.
  hijos?: ItemMenu[];
}

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
  host: { '[class.abierto]': 'menu.abierto()' },
})
export class Sidebar {
  readonly items = input.required<ItemMenu[]>();

  protected readonly menu = inject(MenuMovil);
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  private readonly router = inject(Router);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  // Grupos que el usuario abrió o cerró a mano; si no hay preferencia, el grupo
  // se abre solo cuando la página actual pertenece a él.
  private readonly preferencias = signal<Record<string, boolean>>({});

  // Ruta exacta, pero ignorando parámetros de consulta.
  protected readonly opcionesActivo: IsActiveMatchOptions = {
    paths: 'exact',
    queryParams: 'ignored',
    matrixParams: 'ignored',
    fragment: 'ignored',
  };

  constructor() {
    // En celular el menú se cierra solo al cambiar de página.
    this.router.events
      .pipe(filter((evento) => evento instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menu.cerrar());
  }

  protected contieneRutaActual(item: ItemMenu): boolean {
    const ruta = this.url().split(/[?#]/)[0];
    return !!item.routerLink && (ruta === item.routerLink || ruta.startsWith(`${item.routerLink}/`));
  }

  protected estaAbierto(item: ItemMenu): boolean {
    return this.preferencias()[item.etiqueta] ?? this.contieneRutaActual(item);
  }

  protected alternar(item: ItemMenu): void {
    const abierto = this.estaAbierto(item);
    this.preferencias.update((actual) => ({ ...actual, [item.etiqueta]: !abierto }));
  }
}
