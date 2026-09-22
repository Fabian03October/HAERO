package com.example.backend.service;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import com.example.backend.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ServicioAutenticacion {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public ServicioAutenticacion(UsuarioRepository usuarioRepository,
                                   PasswordEncoder passwordEncoder,
                                   JwtUtil jwtUtil) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(request.getNombreUsuario())
                // mismo mensaje para usuario inexistente, inactivo o contraseña incorrecta (HU01/HU02/HU05)
                .orElseThrow(() -> new BadCredentialsException("Usuario o contraseña incorrectos"));

        if (!usuario.isActivo()) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        if (!passwordEncoder.matches(request.getContrasena(), usuario.getContrasenaHash())) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        usuario.setUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(usuario);

        String token = jwtUtil.generarToken(usuario.getNombreUsuario(), usuario.getRol().getNombre());

        return new LoginResponse(token, usuario.getRol().getNombre(), usuario.isDebeCambiarContrasena());
    }

    public void logout(String nombreUsuario) {
        usuarioRepository.findByNombreUsuario(nombreUsuario).ifPresent(usuario -> {
            usuario.setTokenValidoDesde(LocalDateTime.now());
            usuarioRepository.save(usuario);
        });
    }
}