package com.example.backend.service;

import com.example.backend.entity.BitacoraUsuario;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.BitacoraUsuarioRepository;
import com.example.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Recuperación del administrador. Solo hay un administrador y nadie puede restablecerle la
 * contraseña desde el sistema; si la olvida, el Área de TICs arranca el backend con la
 * variable de entorno ADMIN_RESTABLECER_CONTRASENA (una contraseña temporal). Al arrancar,
 * esa pasa a ser su contraseña, se le obliga a cambiarla, se quita el bloqueo y queda en la
 * bitácora. Después hay que quitar la variable: mientras siga puesta, cada arranque la
 * vuelve a aplicar.
 */
@Component
public class RecuperacionAdministrador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionAdministrador.class);

    private final UsuarioRepository usuarioRepository;
    private final BitacoraUsuarioRepository bitacoraRepository;
    private final PasswordEncoder passwordEncoder;
    private final String contrasenaTemporal;

    public RecuperacionAdministrador(UsuarioRepository usuarioRepository,
                                     BitacoraUsuarioRepository bitacoraRepository,
                                     PasswordEncoder passwordEncoder,
                                     @Value("${ADMIN_RESTABLECER_CONTRASENA:}") String contrasenaTemporal) {
        this.usuarioRepository = usuarioRepository;
        this.bitacoraRepository = bitacoraRepository;
        this.passwordEncoder = passwordEncoder;
        this.contrasenaTemporal = contrasenaTemporal;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (contrasenaTemporal == null || contrasenaTemporal.isBlank()) {
            return;
        }
        ServicioUsuarios.validarContrasena(contrasenaTemporal);
        Usuario admin = usuarioRepository.findAll().stream()
                .filter(u -> "ADMIN".equals(u.getRol().getNombre()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe el usuario administrador"));

        admin.setContrasenaHash(passwordEncoder.encode(contrasenaTemporal));
        admin.setDebeCambiarContrasena(true);
        admin.setActivo(true);
        admin.setIntentosFallidos(0);
        admin.setBloqueadoHasta(null);
        admin.setTokenValidoDesde(LocalDateTime.now());
        usuarioRepository.save(admin);

        BitacoraUsuario registro = new BitacoraUsuario();
        registro.setRealizadoPor("servidor");
        registro.setUsuario(admin);
        registro.setAccion("RESTABLECIMIENTO_CONTRASENA");
        registro.setDetalle("Desde el servidor con ADMIN_RESTABLECER_CONTRASENA");
        bitacoraRepository.save(registro);

        log.warn("Se restableció la contraseña del administrador '{}' con ADMIN_RESTABLECER_CONTRASENA. "
                + "Quita esa variable de entorno y reinicia.", admin.getNombreUsuario());
    }
}
