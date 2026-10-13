package com.umc.pediupatrao.service;

import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {

                    String role = usuario.getRole() == null ? "ATENDENTE" : usuario.getRole().trim().toUpperCase();
                    if (!role.equals("ADMIN") && !role.equals("GERENTE") && !role.equals("ATENDENTE")) {
                        role = "ATENDENTE";
                    }
                    return new User(usuario.getUsername(), usuario.getPassword(),
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}

