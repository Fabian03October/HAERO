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
    titulo: 'Objeto y alcance',
    puntos: [
      'Las presentes políticas regulan el acceso y el uso del sistema HAERO, herramienta institucional del Hospital Regional de Alta Especialidad de Oaxaca para el control del almacenamiento, la distribución y la dispensación de medicamentos.',
      'Son de observancia obligatoria para todo el personal que cuente con una cuenta de acceso al sistema, independientemente de su área o tipo de contratación.',
      'La aceptación de estas políticas es requisito indispensable para utilizar el sistema.',
    ],
  },
  {
    titulo: 'Aviso de privacidad',
    puntos: [
      'El Hospital Regional de Alta Especialidad de Oaxaca es el responsable del tratamiento de los datos personales que se registran en el sistema.',
      'Del personal usuario se recaban el nombre, el nombre de usuario, el rol asignado, la fecha y hora de cada acceso y el registro de las operaciones realizadas.',
      'De los pacientes se tratan únicamente los datos de identificación y clínicos estrictamente necesarios para la dispensación de medicamentos, tales como nombre, número de expediente, diagnóstico y prescripción, obtenidos del sistema institucional del hospital.',
      'Dichos datos se utilizan exclusivamente para el control, la trazabilidad y la seguridad en el manejo de medicamentos, así como para la atención de auditorías y requerimientos de las autoridades competentes.',
      'Los datos personales no serán transferidos a terceros, salvo en los supuestos previstos por la ley.',
      'El tratamiento se realiza conforme a la Ley General de Protección de Datos Personales en Posesión de Sujetos Obligados y demás normatividad aplicable. Los derechos de acceso, rectificación, cancelación y oposición (ARCO) podrán ejercerse ante la Unidad de Transparencia del hospital.',
    ],
  },
  {
    titulo: 'Cuenta de acceso',
    puntos: [
      'La cuenta de acceso (usuario y contraseña) es personal e intransferible. Queda prohibido compartirla o utilizar la cuenta de otra persona.',
      'Toda operación realizada con una cuenta se atribuye a su titular.',
      'La persona usuaria deberá cerrar la sesión al concluir su jornada o al retirarse del equipo de cómputo.',
      'Ante la sospecha de que un tercero conoce su contraseña, la persona usuaria deberá cambiarla de inmediato y notificarlo al administrador del sistema.',
    ],
  },
  {
    titulo: 'Confidencialidad de la información',
    puntos: [
      'La información contenida en el sistema (medicamentos, existencias, solicitudes, movimientos y datos de pacientes) es de uso exclusivamente institucional.',
      'Queda prohibido copiarla, divulgarla, extraerla o utilizarla para fines distintos al desempeño de las funciones asignadas.',
      'La consulta de información de pacientes solo procede cuando sea necesaria para la atención o dispensación correspondiente.',
    ],
  },
  {
    titulo: 'Registro de operaciones y responsabilidad',
    puntos: [
      'Toda entrada, salida, dispensación, cancelación o ajuste queda registrado con el nombre de quien lo realiza, la fecha y la hora. Estos registros no pueden eliminarse.',
      'La persona usuaria es responsable de que la información capturada corresponda fielmente a lo que físicamente recibe, entrega o traslada.',
      'El incumplimiento de las presentes políticas podrá dar lugar a las responsabilidades administrativas, civiles o penales que correspondan conforme a la normatividad aplicable.',
    ],
  },
];

const RESPONSABILIDADES_POR_ROL: Record<Rol, Seccion> = {
  ALMACEN: {
    titulo: 'Obligaciones específicas del personal de Almacén',
    puntos: [
      'Registrar cada entrada con el lote, la caducidad, la cantidad y la ubicación que correspondan físicamente a la mercancía recibida.',
      'Realizar los despachos conforme al orden de caducidad (primeras caducidades, primeras salidas) y descontarlos de la ubicación física de la que se retira el medicamento.',
      'Resguardar por separado los lotes caducados y abstenerse de despacharlos, prestarlos o transferirlos.',
      'Registrar canjes, préstamos y transferencias con la institución y los datos del lote, conforme al documento que los ampara.',
      'Informar a la persona responsable del Almacén cualquier diferencia entre los registros del sistema y las existencias físicas.',
    ],
  },
  FARMACIA: {
    titulo: 'Obligaciones específicas del personal de Farmacia',
    puntos: [
      'Solicitar al Almacén únicamente los medicamentos requeridos, con la clave y la cantidad correctas.',
      'Verificar que lo recibido del Almacén corresponda a lo solicitado y reportar cualquier diferencia.',
      'Dispensar los medicamentos conforme a la prescripción médica y registrar cada salida en el sistema.',
      'Resguardar la información de pacientes y recetas, y abstenerse de consultarla o compartirla sin que medie una razón propia de sus funciones.',
    ],
  },
  SUPERVISION: {
    titulo: 'Obligaciones específicas del personal de Supervisión',
    puntos: [
      'Utilizar la información de consumos, existencias y alertas exclusivamente para el seguimiento y la toma de decisiones del servicio.',
      'Abstenerse de compartir reportes o datos del sistema fuera de las áreas autorizadas.',
      'Notificar a las áreas correspondientes cualquier riesgo de desabasto, sobreabasto o inconsistencia en los registros.',
    ],
  },
  ADMIN: {
    titulo: 'Obligaciones específicas del Administrador del sistema',
    puntos: [
      'Otorgar cuentas únicamente a personal autorizado, con el rol que corresponda a sus funciones.',
      'Desactivar sin demora las cuentas del personal que deje de laborar en el área.',
      'Restablecer contraseñas solo a solicitud de la persona titular de la cuenta, previa verificación de su identidad.',
      'Abstenerse de utilizar las cuentas de otras personas usuarias para realizar operaciones a su nombre.',
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
    return usuario ? this.politicas.aceptacionVigente(usuario) : undefined;
  });

  readonly marcado = signal(false);
  readonly hoy = new Date();

  constructor() {
    this.session.restaurarSesion();
    if (!this.session.usuarioActual()) this.router.navigateByUrl('/login');
  }

  readonly guardando = signal(false);
  readonly error = signal('');

  async aceptar(): Promise<void> {
    const usuario = this.usuario();
    if (!usuario || !this.marcado() || this.guardando()) return;
    this.guardando.set(true);
    const error = await this.politicas.aceptar();
    this.guardando.set(false);
    if (error) {
      this.error.set(error);
      return;
    }
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
