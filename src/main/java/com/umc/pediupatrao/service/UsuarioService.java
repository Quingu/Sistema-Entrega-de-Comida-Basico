package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Role;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }


    @PreAuthorize("hasRole('ADMIN')")
    public Usuario salvarUsuario(Usuario usuario) {
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username é obrigatório.");
        }
        if (buscarPorUsername(usuario.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username já existe.");
        }
        Role.of(usuario.getRole());
        usuario.setRole(usuario.getRole().trim().toUpperCase());
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("Senha é obrigatória.");
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }


    @PreAuthorize("hasRole('ADMIN')")
    public void atualizarUsuario(String id, Usuario usuarioAtualizado) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        String logado = currentUsername();
        boolean mexendoEmSi = existente.getUsername().equalsIgnoreCase(logado);

        if (usuarioAtualizado.getUsername() != null && !usuarioAtualizado.getUsername().isBlank()
                && !usuarioAtualizado.getUsername().equals(existente.getUsername())) {
            if (buscarPorUsername(usuarioAtualizado.getUsername()).isPresent()) {
                throw new IllegalArgumentException("Username já existe.");
            }
            existente.setUsername(usuarioAtualizado.getUsername());
        }
        if (usuarioAtualizado.getRole() != null && !usuarioAtualizado.getRole().isBlank()) {
            Role novo = Role.of(usuarioAtualizado.getRole());
            if (mexendoEmSi && novo != Role.of(existente.getRole())) {
                throw new IllegalArgumentException("Você não pode alterar o próprio perfil (anti auto-elevação/rebaixamento).");
            }
            if (Role.of(existente.getRole()) == Role.ADMIN && novo != Role.ADMIN && contarAdmins() <= 1) {
                throw new IllegalArgumentException("Não é permitido rebaixar o último administrador.");
            }
            existente.setRole(novo.name());
        }
        if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
            existente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
        }
        usuarioRepository.save(existente);
    }


    @PreAuthorize("hasRole('ADMIN')")
    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        if (existente.getUsername().equalsIgnoreCase(currentUsername())) {
            throw new IllegalArgumentException("Você não pode excluir a própria conta.");
        }
        if (Role.of(existente.getRole()) == Role.ADMIN && contarAdmins() <= 1) {
            throw new IllegalArgumentException("Não é permitido excluir o último administrador.");
        }
        usuarioRepository.deleteById(id);
    }

    private long contarAdmins() {
        long n = 0;
        for (Usuario u : usuarioRepository.findAll()) {
            try {
                if (Role.of(u.getRole()) == Role.ADMIN) n++;
            } catch (IllegalArgumentException ignored) {

            }
        }
        return n;
    }

    private String currentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "";
        }
    }
}

