import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';
import { ADMIN_MENU } from '../admin-nav';
import { AccionBitacora, RegistroBitacora, Usuario, UsuariosApi } from '../usuarios-api';

const ACCION_ETIQUETA: Record<AccionBitacora, string> = {
  ALTA: 'Alta',
  EDICION: 'Cambio de datos',
  DESACTIVACION: 'Desactivación',
  REACTIVACION: 'Reactivación',
  RESTABLECIMIENTO_CONTRASENA: 'Contraseña restablecida',
};

function hoyMenos(dias: number): string {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() - dias);
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

// Administración · Bitácora (HU41): quién dio de alta, cambió, desactivó, reactivó o
// restableció la contraseña de cada cuenta. Solo consulta; nadie la edita ni la borra.
@Component({
  selector: 'app-bitacora-usuarios',
  imports: [Topbar, Sidebar, FormsModule, DatePipe],
  templateUrl: './bitacora-usuarios.html',
  styleUrls: ['../gestion-usuarios/gestion-usuarios.css', './bitacora-usuarios.css'],
})
export class BitacoraUsuarios {
  private readonly usuariosApi = inject(UsuariosApi);

  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  protected readonly accionEtiqueta = ACCION_ETIQUETA;
  readonly items: ItemMenu[] = ADMIN_MENU;

  readonly usuarios = signal<Usuario[]>([]);
  readonly registros = signal<RegistroBitacora[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');

  usuarioId: number | null = null;
  desde = hoyMenos(30);
  hasta = hoyMenos(0);

  readonly total = computed(() => this.registros().length);

  constructor() {
    this.usuariosApi.listar().subscribe({ next: (usuarios) => this.usuarios.set(usuarios), error: () => {} });
    this.consultar();
  }

  consultar(): void {
    if (this.desde && this.hasta && this.desde > this.hasta) {
      this.error.set('La fecha "desde" no puede ser posterior a "hasta".');
      return;
    }
    this.cargando.set(true);
    this.error.set('');
    this.usuariosApi
      .bitacora({ usuarioId: this.usuarioId ?? undefined, desde: this.desde || undefined, hasta: this.hasta || undefined })
      .subscribe({
        next: (registros) => {
          this.registros.set(registros);
          this.cargando.set(false);
        },
        error: (err) => {
          this.registros.set([]);
          this.error.set(err?.error?.mensaje ?? 'No se pudo consultar la bitácora.');
          this.cargando.set(false);
        },
      });
  }
}
