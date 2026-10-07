package com.example.backend.service;

import com.example.backend.dto.AceptacionPoliticasResponse;
import com.example.backend.dto.AceptarPoliticasRequest;
import com.example.backend.dto.CambiarContrasenaRequest;
import com.example.backend.dto.CrearUsuarioRequest;
import com.example.backend.dto.EditarUsuarioRequest;
import com.example.backend.dto.RestablecerContrasenaRequest;
import com.example.backend.dto.UsuarioResponse;
import com.example.backend.entity.Rol;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.RolRepository;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public ServicioUsuarios(UsuarioRepository usuarioRepository,
                             RolRepository rolRepository,
                             PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioResponse> listarUsuarios(String nombre) {
        List<Usuario> usuarios = (nombre == null || nombre.isBlank())
                ? usuarioRepository.findAll()
                : usuarioRepository.findByNombreCompletoContainingIgnoreCase(nombre);

        return usuarios.stream().map(this::aRespuesta).toList();
    }

    public UsuarioResponse crearUsuario(CrearUsuarioRequest request) {
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

        return aRespuesta(usuarioRepository.save(usuario));
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

        usuario.setNombreCompleto(request.getNombreCompleto());
        usuario.setCorreo(request.getCorreo());
        usuario.setRol(rol);

        return aRespuesta(usuarioRepository.save(usuario));
    }

    public UsuarioResponse desactivarUsuario(Long id, String nombreUsuarioSolicitante) {
        Usuario usuario = buscarUsuario(id);

        if (usuario.getNombreUsuario().equals(nombreUsuarioSolicitante)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El administrador no puede desactivarse a sí mismo");
        }

        usuario.setActivo(false);
        return aRespuesta(usuarioRepository.save(usuario));
    }

    public UsuarioResponse reactivarUsuario(Long id) {
        Usuario usuario = buscarUsuario(id);
        usuario.setActivo(true);
        return aRespuesta(usuarioRepository.save(usuario));
    }

    public UsuarioResponse restablecerContrasena(Long id, RestablecerContrasenaRequest request) {
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

        return aRespuesta(usuarioRepository.save(usuario));
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
