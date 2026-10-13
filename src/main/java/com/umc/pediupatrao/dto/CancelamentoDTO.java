package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelamentoDTO {
    @NotBlank(message = "Justificativa do cancelamento é obrigatória.")
    @Size(min = 5, max = 500, message = "Justificativa deve ter entre 5 e 500 caracteres.")
    private String justificativa;
}

