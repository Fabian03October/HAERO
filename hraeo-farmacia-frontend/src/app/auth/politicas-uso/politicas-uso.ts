import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { ROL_ETIQUETA, RUTA_POR_ROL, Rol, Session } from '../session';
import { FECHA_POLITICAS, PoliticasUso, VERSION_POLITICAS } from '../politicas';

interface Seccion {
  titulo: string;
  puntos: string[];
}

// Borrador: el texto definitivo lo debe revisar el área jurídica o la Unidad de
// Transparencia del hospital. Al cambiarlo, subir VERSION_POLITICAS en politicas.ts.
const SECCIONES_COMUNES: Seccion[] = [
  {
    titulo: 'Aviso de privacidad',
    puntos: [
      'El sistema registra tu nombre, usuario, rol, fecha y hora de acceso y cada operación que realizas, con el fin de controlar el manejo de medicamentos del hospital.',
      'La información de medicamentos, existencias, solicitudes y movimientos es de uso interno. No debes copiarla, compartirla ni usarla para fines distintos a tus funciones.',
      'Los datos personales se tratan conforme a la normatividad aplicable en materia de protección de datos personales en posesión de sujetos obligados.',
    ],
  },
  {
    titulo: 'Uso de tu cuenta',
    puntos: [
      'Tu usuario y contraseña son personales e intransferibles. No los compartas ni uses la cuenta de otra persona.',
      'Todo lo que se haga con tu cuenta queda registrado a tu nombre. Cierra sesión al terminar o si dejas el equipo.',
      'Si sospechas que alguien conoce tu contraseña, cámbiala y avisa al administrador del sistema.',
    ],
  },
  {
    titulo: 'Registro y responsabilidad',
    puntos: [
      'Cada entrada, salida, despacho, cancelación o ajuste queda registrado con tu nombre, la fecha y la hora, y no se puede borrar.',
      'Eres responsable de que lo que capturas coincida con lo que físicamente recibes, entregas o mueves.',
      'El incumplimiento de estas políticas puede dar lugar a las responsabilidades administrativas que correspondan conforme a la normatividad del hospital.',
    ],
  },
];

const RESPONSABILIDADES_POR_ROL: Record<Rol, Seccion> = {
  ALMACEN: {
    titulo: 'Responsabilidades del personal de Almacén',
    puntos: [
      'Registrar cada entrada contra su pedido, con el lote, la caducidad, la cantidad y la ubicación reales de la caja.',
      'Despachar respetando el orden de caducidad (primero lo que caduca antes) y descontar de la ubicación física de donde sale el medicamento.',
      'Apartar en "caducados" los lotes vencidos y no despacharlos, prestarlos ni transferirlos.',
      'Registrar canjes, préstamos y transferencias con la institución y los datos del lote, tal como consta en su documento.',
      'Reportar a la encargada de Almacén cualquier diferencia entre el sistema y lo que hay físicamente.',
    ],
  },
  FARMACIA: {
    titulo: 'Responsabilidades del personal de Farmacia',
    puntos: [
      'Solicitar a Almacén solo el medicamento que se necesita, con la clave y la cantidad correctas.',
      'Verificar que lo recibido de Almacén coincide con lo solicitado y reportar cualquier diferencia.',
      'Dispensar conforme a la receta y registrar cada salida a tu nombre.',
      'Resguardar la información de pacientes y recetas; no consultarla ni compartirla sin motivo de tu función.',
    ],
  },
  SUPERVISION: {
    titulo: 'Responsabilidades de Supervisión',
    puntos: [
      'Usar la información de consumo, existencias y alertas solo para el seguimiento y la toma de decisiones del servicio.',
      'No compartir reportes ni datos del sistema fuera de las áreas autorizadas.',
      'Dar aviso a las áreas correspondientes cuando detectes riesgos de desabasto, sobreabasto o diferencias en los registros.',
    ],
  },
  ADMIN: {
    titulo: 'Responsabilidades del Administrador',
    puntos: [
      'Dar de alta solo a personal autorizado y asignarle el rol que corresponde a su función.',
      'Desactivar sin demora las cuentas del personal que ya no labora en el área.',
      'Restablecer contraseñas solo a petición de la persona dueña de la cuenta, verificando su identidad.',
      'No usar las cuentas de otros usuarios para hacer operaciones en su nombre.',
    ],
  },
};

// Pantalla de políticas de uso y privacidad. Se muestra al entrar hasta que el
// usuario la acepta (el guard lo manda aquí) y también se puede consultar después.
@Component({
  selector: 'app-politicas-uso',
  imports: [DatePipe],
  templateUrl: './politicas-uso.html',
  styleUrl: './politicas-uso.css',
})
export class PoliticasUsoPage {
  protected readonly session = inject(Session);
  private readonly politicas = inject(PoliticasUso);
  private readonly router = inject(Router);

  protected readonly version = VERSION_POLITICAS;
  protected readonly fechaVersion = FECHA_POLITICAS;
  protected readonly secciones = SECCIONES_COMUNES;
  protected readonly rolEtiqueta = ROL_ETIQUETA;

  readonly usuario = this.session.usuarioActual;
  readonly responsabilidades = computed(() => {
    const usuario = this.usuario();
    return usuario ? RESPONSABILIDADES_POR_ROL[usuario.rol] : null;
  });
  readonly aceptacion = computed(() => {
    const usuario = this.usuario();
    return usuario ? this.politicas.aceptacionVigente(usuario.nombreUsuario) : undefined;
  });

  readonly marcado = signal(false);
  readonly hoy = new Date();

  constructor() {
    this.session.restaurarSesion();
    if (!this.session.usuarioActual()) this.router.navigateByUrl('/login');
  }

  aceptar(): void {
    const usuario = this.usuario();
    if (!usuario || !this.marcado()) return;
    this.politicas.aceptar(usuario);
    this.router.navigateByUrl(RUTA_POR_ROL[usuario.rol]);
  }

  // No aceptar = no puede usar el sistema: se cierra la sesión.
  rechazar(): void {
    this.session.cerrarSesion();
    this.router.navigateByUrl('/login');
  }

  volver(): void {
    const usuario = this.usuario();
    this.router.navigateByUrl(usuario ? RUTA_POR_ROL[usuario.rol] : '/login');
  }
}
