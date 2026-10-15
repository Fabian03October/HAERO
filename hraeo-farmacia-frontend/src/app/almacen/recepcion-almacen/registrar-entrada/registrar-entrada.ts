import { Component } from '@angular/core';
import { CapturaIntelisis } from '../captura-intelisis/captura-intelisis';

// Recepción · Registrar entrada (HU10). Se escanea la caja, se elige el registro de
// Intelisis (compra con su pedido y contrato, donación o traspaso) y Almacén pone la
// ubicación y las cajas que contó. Lote y caducidad no se teclean: son los de Intelisis.
@Component({
  selector: 'app-registrar-entrada',
  imports: [CapturaIntelisis],
  templateUrl: './registrar-entrada.html',
  styleUrls: ['../recepcion-comun.css'],
})
export class RegistrarEntrada {}
