package com.umc.pediupatrao.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.umc.pediupatrao.dto.CancelamentoDTO;
import com.umc.pediupatrao.dto.DescontoDTO;
import com.umc.pediupatrao.dto.PedidoCreateDTO;
import com.umc.pediupatrao.dto.StatusDTO;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE','ADMIN')")
    public Pedido criar(@Valid @RequestBody PedidoCreateDTO dto) {
        return pedidoService.criar(dto);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Pedido> listar() {
        return pedidoService.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Pedido buscar(@PathVariable String id) {
        return pedidoService.buscar(id);
    }


    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido transitar(@PathVariable String id, @Valid @RequestBody StatusDTO dto) {
        return pedidoService.transitar(id, dto);
    }


    @PostMapping("/{id}/desconto")
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido desconto(@PathVariable String id, @Valid @RequestBody DescontoDTO dto) {
        return pedidoService.aplicarDesconto(id, dto);
    }


    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(@PathVariable String id, @Valid @RequestBody CancelamentoDTO dto) {
        return pedidoService.cancelar(id, dto);
    }
}

