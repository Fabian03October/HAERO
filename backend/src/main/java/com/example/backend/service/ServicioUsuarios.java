package com.example.backend.service;

import com.example.backend.dto.AceptacionPoliticasResponse;
import com.example.backend.dto.AceptarPoliticasRequest;
import com.example.backend.dto.BitacoraUsuarioResponse;
import com.example.backend.dto.CambiarContrasenaRequest;
import com.example.backend.dto.CrearUsuarioRequest;
import com.example.backend.dto.EditarUsuarioRequest;
import com.example.backend.dto.RestablecerContrasenaRequest;
import com.example.backend.dto.UsuarioResponse;
import com.example.backend.entity.BitacoraUsuario;
import com.example.backend.entity.Rol;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.BitacoraUsuarioRepository;
import com.example.backend.repository.RolRepository;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ServicioUsuarios {

    private static final Pattern CORREO_VALIDO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int LONGITUD_MINIMA_CONTRASENA = 8;
    static final String MENSAJE_CONTRASENA =
            "La contraseña debe tener al menos " + LONGITUD_MINIMA_CONTRASENA + " caracteres, con al menos una letra y un número";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final BitacoraUsuarioRepository bitacoraRepository;

    public ServicioUsuarios(UsuarioRepository usuarioRepository,
                             RolRepository rolRepository,
                             PasswordEncoder passwordEncoder,
                             BitacoraUsuarioRepository bitacoraRepository) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.bitacoraRepository = bitacoraRepository;
    }

    public List<UsuarioResponse> listarUsuarios(String nombre) {
        List<Usuario> usuarios = (nombre == null || nombre.isBlank())
                ? usuarioRepository.findAll()
                : usuarioRepository.findByNombreCompletoContainingIgnoreCase(nombre);

        return usuarios.stream().map(this::aRespuesta).toList();
    }

    public UsuarioResponse crearUsuario(CrearUsuarioRequest request, String nombreUsuarioSolicitante) {
        if (usuarioRepository.existsByNombreUsuario(request.getNombreUsuario())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya existe");
        }

        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        validarCorreo(request.getCorreo());
        validarContrasena(request.getContrasenaTemporal());

        Rol rol = buscarRol(request.getRol());
        if (esAdmin(rol)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pueden crear más administradores; solo puede existir uno");
        }

        Usuario usuario = new Usuario();
        usuario.setNombreCompleto(request.getNombreCompleto());
        usuario.setNombreUsuario(request.getNombreUsuario());
        usuario.setCorreo(request.getCorreo());
        usuario.setContrasenaHash(passwordEncoder.encode(request.getContrasenaTemporal()));
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setDebeCambiarContrasena(true);

        Usuario guardado = usuarioRepository.save(usuario);
        registrar(nombreUsuarioSolicitante, guardado, "ALTA", "Rol " + rol.getNombre());
        return aRespuesta(guardado);
    }

    public UsuarioResponse obtenerUsuario(Long id) {
        return aRespuesta(buscarUsuario(id));
    }

    public UsuarioResponse editarUsuario(Long id, EditarUsuarioRequest request, String nombreUsuarioSolicitante) {
        Usuario usuario = buscarUsuario(id);

        if (usuario.getNombreUsuario().equals(nombreUsuarioSolicitante)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El administrador no puede editar su propio usuario");
        }

        if (usuarioRepository.existsByCorreoAndIdNot(request.getCorreo(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        validarCorreo(request.getCorreo());

        Rol rol = buscarRol(request.getRol());
        if (esAdmin(rol)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede asignar el rol Administrador; solo puede existir uno");
        }

        List<String> cambios = new ArrayList<>();
        if (!java.util.Objects.equals(usuario.getNombreCompleto(), request.getNombreCompleto())) {
            cambios.add("Nombre: " + usuario.getNombreCompleto() + " → " + request.getNombreCompleto());
        }
        if (!java.util.Objects.equals(usuario.getCorreo(), request.getCorreo())) {
            cambios.add("Correo: " + usuario.getCorreo() + " → " + request.getCorreo());
        }
        boolean cambiaRol = !rol.getNombre().equals(usuario.getRol().getNombre());
        if (cambiaRol) {
            cambios.add("Rol: " + usuario.getRol().getNombre() + " → " + rol.getNombre());
            // HU07 CA3: con otro rol tiene que volver a entrar, para que su pantalla y su
            // menú sean los del rol nuevo.
            usuario.setTokenValidoDesde(LocalDateTime.now());
        }

        usuario.setNombreCompleto(request.getNombreCompleto());
        usuario.setCorreo(request.getCorreo());
        usuario.setRol(rol);

        Usuario guardado = usuarioRepository.save(usuario);
        if (!cambios.isEmpty()) {
            registrar(nombreUsuarioSolicitante, guardado, "EDICION", String.join("; ", cambios));
        }
        return aRespuesta(guardado);
    }

    public UsuarioResponse desactivarUsuario(Long id, String nombreUsuarioSolicitante) {
        Usuario usuario = buscarUsuario(id);

        if (usuario.getNombreUsuario().equals(nombreUsuarioSolicitante)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El administrador no puede desactivarse a sí mismo");
        }

        usuario.setActivo(false);
        Usuario guardado = usuarioRepository.save(usuario);
        registrar(nombreUsuarioSolicitante, guardado, "DESACTIVACION", null);
        return aRespuesta(guardado);
    }

    public UsuarioResponse reactivarUsuario(Long id, String nombreUsuarioSolicitante) {
        Usuario usuario = buscarUsuario(id);
        usuario.setActivo(true);
        Usuario guardado = usuarioRepository.save(usuario);
        registrar(nombreUsuarioSolicitante, guardado, "REACTIVACION", null);
        return aRespuesta(guardado);
    }

    public UsuarioResponse restablecerContrasena(Long id, RestablecerContrasenaRequest request, String nombreUsuarioSolicitante) {
        Usuario usuario = buscarUsuario(id);
        if (esAdmin(usuario.getRol())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede restablecer la contraseña del administrador");
        }

        validarContrasena(request.getContrasenaTemporal());

        usuario.setContrasenaHash(passwordEncoder.encode(request.getContrasenaTemporal()));
        usuario.setDebeCambiarContrasena(true);
        usuario.setTokenValidoDesde(java.time.LocalDateTime.now());
        // Restablecer también quita el bloqueo por intentos fallidos.
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);

        Usuario guardado = usuarioRepository.save(usuario);
        // Solo se anota que se restableció; la contraseña temporal nunca se guarda aquí.
        registrar(nombreUsuarioSolicitante, guardado, "RESTABLECIMIENTO_CONTRASENA", null);
        return aRespuesta(guardado);
    }

    /** Bitácora de administración (HU41), de un usuario o de todos, por fechas (incluye el día "hasta"). */
    public List<BitacoraUsuarioResponse> bitacora(Long usuarioId, LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = (desde == null ? LocalDate.now().minusDays(30) : desde).atStartOfDay();
        LocalDateTime fin = (hasta == null ? LocalDate.now() : hasta).plusDays(1).atStartOfDay();
        List<BitacoraUsuario> registros = usuarioId == null
                ? bitacoraRepository.findByFechaBetweenOrderByFechaDesc(inicio, fin)
                : bitacoraRepository.findByUsuarioIdAndFechaBetweenOrderByFechaDesc(usuarioId, inicio, fin);
        return registros.stream()
                .map(b -> new BitacoraUsuarioResponse(b.getId(), b.getFecha(), b.getRealizadoPor(), b.getUsuario().getId(),
                        b.getUsuario().getNombreUsuario(), b.getUsuario().getNombreCompleto(), b.getAccion(), b.getDetalle()))
                .toList();
    }

    private void registrar(String realizadoPor, Usuario usuario, String accion, String detalle) {
        BitacoraUsuario registro = new BitacoraUsuario();
        registro.setRealizadoPor(realizadoPor == null ? "sistema" : realizadoPor);
        registro.setUsuario(usuario);
        registro.setAccion(accion);
        registro.setDetalle(detalle == null || detalle.length() <= 400 ? detalle : detalle.substring(0, 400));
        bitacoraRepository.save(registro);
    }

    /**
     * Registra en el servidor que el usuario aceptó esta versión de las políticas de
     * uso y privacidad, con fecha y hora; antes solo quedaba en su navegador.
     */
    public AceptacionPoliticasResponse aceptarPoliticas(String nombreUsuario, AceptarPoliticasRequest request) {
        String version = request == null || request.getVersion() == null ? "" : request.getVersion().trim();
        if (version.isEmpty() || version.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la versión de las políticas que se aceptó");
        }
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        usuario.setVersionPoliticasAceptada(version);
        usuario.setFechaAceptacionPoliticas(java.time.LocalDateTime.now());
        usuarioRepository.save(usuario);
        return new AceptacionPoliticasResponse(usuario.getVersionPoliticasAceptada(), usuario.getFechaAceptacionPoliticas());
    }

    public void cambiarContrasenaPropia(String nombreUsuario, CambiarContrasenaRequest request) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // Todos, incluido el administrador, pueden cambiar su propia contraseña.
        if (!passwordEncoder.matches(request.getContrasenaActual(), usuario.getContrasenaHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }

        validarContrasena(request.getContrasenaNueva());
        if (passwordEncoder.matches(request.getContrasenaNueva(), usuario.getContrasenaHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta de la actual");
        }

        usuario.setContrasenaHash(passwordEncoder.encode(request.getContrasenaNueva()));
        usuario.setDebeCambiarContrasena(false);
        usuario.setTokenValidoDesde(java.time.LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    private Usuario buscarUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private boolean esAdmin(Rol rol) {
        return "ADMIN".equals(rol.getNombre());
    }

    private Rol buscarRol(String nombreRol) {
        return rolRepository.findByNombre(nombreRol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido: " + nombreRol));
    }

    private void validarCorreo(String correo) {
        if (correo == null || !CORREO_VALIDO.matcher(correo).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo no tiene un formato válido");
        }
    }

    /** Mínimo 8 caracteres, con al menos una letra y un número (aplica también a las temporales). */
    static void validarContrasena(String contrasena) {
        boolean valida = contrasena != null
                && contrasena.length() >= LONGITUD_MINIMA_CONTRASENA
                && contrasena.chars().anyMatch(Character::isLetter)
                && contrasena.chars().anyMatch(Character::isDigit);
        if (!valida) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MENSAJE_CONTRASENA);
        }
    }

    private UsuarioResponse aRespuesta(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombreCompleto(),
                usuario.getNombreUsuario(),
                usuario.getCorreo(),
                usuario.getRol().getNombre(),
                usuario.isActivo(),
                usuario.isDebeCambiarContrasena()
        );
    }
}
