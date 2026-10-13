package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatusDTO {
    @NotBlank(message = "status é obrigatório.")
    private String status;
}

