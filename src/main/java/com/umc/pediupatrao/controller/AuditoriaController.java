package com.umc.pediupatrao.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.service.AuditoriaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping
    public List<Auditoria> listar() {
        return auditoriaService.listarTodas();
    }

    @GetMapping("/{entidade}/{id}")
    public List<Auditoria> porEntidade(@PathVariable String entidade, @PathVariable String id) {
        return auditoriaService.porEntidade(entidade, id);
    }
}

