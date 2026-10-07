package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.CargaInicialResponse;
import com.example.backend.almacen.dto.ErrorRenglonCargaInicial;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.repository.ExistenciaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HU21 (carga inicial de inventario) — TEMPORAL: importa desde un archivo CSV
 * subido a mano. Cuando el equipo de Contratos exponga su endpoint, esta clase
 * debe cambiar para leer los renglones de ahi en lugar del archivo (ver memoria
 * de proyecto "carga inicial temporal").
 *
 * A diferencia del documento de diseño (que propone /validar y /confirmar como
 * dos llamadas separadas, con estado intermedio en el servidor), aqui se valida
 * y se guarda en una sola operacion: si hay algun renglon con error, no se
 * guarda nada (CA2) y no hace falta mantener un estado de "validacion pendiente"
 * entre dos peticiones.
 */
@Service
public class ServicioCargaInicial {

    private final MedicamentoRepository medicamentoRepository;
    private final LoteRepository loteRepository;
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;

    public ServicioCargaInicial(MedicamentoRepository medicamentoRepository,
                                 LoteRepository loteRepository,
                                 ExistenciaRepository existenciaRepository,
                                 MovimientoRepository movimientoRepository,
                                 UsuarioRepository usuarioRepository) {
        this.medicamentoRepository = medicamentoRepository;
        this.loteRepository = loteRepository;
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public CargaInicialResponse cargar(MultipartFile archivo, String nombreUsuarioSolicitante) {
        List<String> lineas;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
            lineas = reader.lines().toList();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");
        }

        return cargar(lineas, nombreUsuarioSolicitante);
    }

    @Transactional
    public CargaInicialResponse cargar(List<String> lineasConEncabezado, String nombreUsuarioSolicitante) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuarioSolicitante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (lineasConEncabezado == null || lineasConEncabezado.size() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
        }

        List<String> renglones = lineasConEncabezado.subList(1, lineasConEncabezado.size());
        List<ErrorRenglonCargaInicial> errores = new ArrayList<>();
        List<RenglonValido> validos = new ArrayList<>();
        // Para detectar renglones repetidos y el mismo lote con dos caducidades en el archivo.
        Set<String> lotesYUbicacionesVistos = new HashSet<>();
        Map<String, LocalDate> caducidadPorLote = new HashMap<>();

        int numeroFila = 1;
        for (String linea : renglones) {
            numeroFila++;
            if (linea == null || linea.isBlank()) {
                continue;
            }

            String[] campos = linea.split(",", -1);
            if (campos.length < 6) {
                errores.add(new ErrorRenglonCargaInicial(numeroFila,
                        "Faltan columnas (se esperan: clave, lote, caducidad, proveedor, ubicacion, cajas)"));
                continue;
            }

            String clave = campos[0].trim();
            String numeroLote = campos[1].trim();
            String caducidadTexto = campos[2].trim();
            String proveedor = campos[3].trim();
            String ubicacion = campos[4].trim();
            String cantidadTexto = campos[5].trim();
            // Columna opcional "CONSUMO PROM." de la hoja (HU16): respaldo del CPM
            // mientras la clave no tenga salidas propias registradas en el sistema.
            String consumoPromedioTexto = campos.length > 6 ? campos[6].trim() : "";

            Medicamento medicamento = medicamentoRepository.findById(clave).orElse(null);
            if (medicamento == null) {
                errores.add(new ErrorRenglonCargaInicial(numeroFila,
                        "La clave " + clave + " no está registrada en el catálogo de medicamentos"));
                continue;
            }

            Double consumoPromedio = null;
            if (!consumoPromedioTexto.isBlank()) {
                try {
                    consumoPromedio = Double.parseDouble(consumoPromedioTexto);
                    if (consumoPromedio <= 0) {
                        consumoPromedio = null;
                    }
                } catch (NumberFormatException e) {
                    errores.add(new ErrorRenglonCargaInicial(numeroFila, "El consumo promedio debe ser numérico"));
                    continue;
                }
            }

            LocalDate caducidad;
            try {
                caducidad = LocalDate.parse(caducidadTexto);
            } catch (DateTimeParseException e) {
                errores.add(new ErrorRenglonCargaInicial(numeroFila, "Caducidad inválida (use AAAA-MM-DD)"));
                continue;
            }

            int cantidad;
            try {
                cantidad = Integer.parseInt(cantidadTexto);
                if (cantidad <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                errores.add(new ErrorRenglonCargaInicial(numeroFila, "La cantidad de cajas debe ser un entero mayor a cero"));
                continue;
            }

            if (numeroLote.isBlank() || proveedor.isBlank() || ubicacion.isBlank()) {
                errores.add(new ErrorRenglonCargaInicial(numeroFila, "Faltan datos obligatorios (lote, proveedor o ubicación)"));
                continue;
            }

            // Evita duplicar el inventario si se vuelve a subir el mismo archivo.
            if (!loteRepository.findByMedicamentoClaveAndNumeroLoteIgnoreCaseAndEstatus(clave, numeroLote, "DISPONIBLE").isEmpty()) {
                errores.add(new ErrorRenglonCargaInicial(numeroFila, "El lote " + numeroLote + " de la clave " + clave
                        + " ya está en el inventario; la carga inicial no lo vuelve a sumar. Quítalo del archivo o regístralo como entrada"));
                continue;
            }
            String llaveLote = clave + "|" + numeroLote.toUpperCase();
            if (!lotesYUbicacionesVistos.add(llaveLote + "|" + ubicacion.toUpperCase())) {
                errores.add(new ErrorRenglonCargaInicial(numeroFila, "Renglón repetido: el lote " + numeroLote + " en " + ubicacion
                        + " ya aparece antes en el archivo"));
                continue;
            }
            LocalDate caducidadPrevia = caducidadPorLote.putIfAbsent(llaveLote, caducidad);
            if (caducidadPrevia != null && !caducidadPrevia.equals(caducidad)) {
                errores.add(new ErrorRenglonCargaInicial(numeroFila, "El lote " + numeroLote + " aparece con dos caducidades distintas ("
                        + caducidadPrevia + " y " + caducidad + ")"));
                continue;
            }

            validos.add(new RenglonValido(medicamento, numeroLote, caducidad, proveedor, ubicacion, cantidad, consumoPromedio));
        }

        if (!errores.isEmpty()) {
            return new CargaInicialResponse(false, renglones.size(), 0, errores);
        }

        // Un mismo lote repartido en varias ubicaciones queda como un solo lote con varias existencias.
        Map<String, Lote> lotesCreados = new HashMap<>();
        for (RenglonValido renglon : validos) {
            if (renglon.consumoPromedioHoja() != null) {
                renglon.medicamento().setConsumoPromedioHoja(renglon.consumoPromedioHoja());
            }

            String llaveLote = renglon.medicamento().getClave() + "|" + renglon.numeroLote().toUpperCase();
            Lote lote = lotesCreados.get(llaveLote);
            if (lote == null) {
                lote = new Lote();
                lote.setMedicamento(renglon.medicamento());
                lote.setNumeroLote(renglon.numeroLote());
                lote.setCaducidad(renglon.caducidad());
                lote.setProveedor(renglon.proveedor());
                lote.setEstatus("DISPONIBLE");
                lote = loteRepository.save(lote);
                lotesCreados.put(llaveLote, lote);
            }

            Existencia existencia = new Existencia();
            existencia.setLote(lote);
            existencia.setUbicacion(renglon.ubicacion());
            existencia.setCantidadCajas(renglon.cantidad());
            existencia = existenciaRepository.save(existencia);

            Movimiento movimiento = new Movimiento();
            movimiento.setExistencia(existencia);
            movimiento.setTipo("CARGA_INICIAL");
            movimiento.setCantidadCajas(renglon.cantidad());
            movimiento.setUsuario(usuario);
            movimientoRepository.save(movimiento);
        }

        return new CargaInicialResponse(true, validos.size(), validos.size(), List.of());
    }

    private record RenglonValido(Medicamento medicamento, String numeroLote, LocalDate caducidad,
                                  String proveedor, String ubicacion, int cantidad, Double consumoPromedioHoja) {
    }
}
