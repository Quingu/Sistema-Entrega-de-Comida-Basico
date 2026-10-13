package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItemPedidoDTO {
    @NotBlank(message = "produtoId é obrigatório.")
    private String produtoId;

    @Min(value = 1, message = "quantidade deve ser ao menos 1.")
    private int quantidade;
}

