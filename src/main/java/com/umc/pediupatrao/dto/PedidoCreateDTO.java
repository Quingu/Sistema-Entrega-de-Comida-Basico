package com.umc.pediupatrao.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PedidoCreateDTO {
    @NotBlank(message = "clienteId é obrigatório.")
    private String clienteId;

    @NotEmpty(message = "Informe ao menos um item.")
    @Valid
    private List<ItemPedidoDTO> itens = new ArrayList<>();

    public void setItens(List<ItemPedidoDTO> itens) {
        this.itens = itens == null ? new ArrayList<>() : itens;
    }
}

