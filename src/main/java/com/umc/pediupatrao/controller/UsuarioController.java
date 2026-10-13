package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<Iterable<Usuario>> listarTodos() {
        return ResponseEntity.ok(sanitized(usuarioService.listarTodos()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscarPorId(@PathVariable String id) {
        return usuarioService.buscarPorId(id)
                .map(u -> ResponseEntity.ok(sanitize(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Usuario> criarUsuario(@RequestBody Usuario usuario) {
        Usuario salvo = usuarioService.salvarUsuario(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(sanitize(salvo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> atualizarUsuario(@PathVariable String id, @RequestBody Usuario usuarioAtualizado) {
        if (usuarioService.buscarPorId(id).isEmpty()) return ResponseEntity.notFound().build();
        usuarioService.atualizarUsuario(id, usuarioAtualizado);
        return usuarioService.buscarPorId(id).map(u -> ResponseEntity.ok(sanitize(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable String id) {
        if (usuarioService.buscarPorId(id).isEmpty()) return ResponseEntity.notFound().build();
        usuarioService.deletarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    private Usuario sanitize(Usuario u) {
        Usuario c = new Usuario();
        c.setId(u.getId());
        c.setUsername(u.getUsername());
        c.setRole(u.getRole());
        c.setPassword(null);
        return c;
    }

    private Iterable<Usuario> sanitized(Iterable<Usuario> list) {
        java.util.List<Usuario> out = new java.util.ArrayList<>();
        list.forEach(u -> out.add(sanitize(u)));
        return out;
    }
}

