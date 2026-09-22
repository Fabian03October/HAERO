import { Component, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Session } from '../../auth/session';

@Component({
  selector: 'app-topbar',
  imports: [RouterLink],
  templateUrl: './topbar.html',
  styleUrl: './topbar.css',
})
export class Topbar {
  readonly nombreCompleto = input.required<string>();
  readonly rol = input.required<string>();

  private readonly session = inject(Session);
  private readonly router = inject(Router);

  cerrarSesion(): void {
    this.session.cerrarSesion();
    this.router.navigateByUrl('/login');
  }
}
