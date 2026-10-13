package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DescontoDTO {
    @NotNull(message = "percentual é obrigatório.")
    @DecimalMin(value = "0.0", message = "Desconto mínimo é 0%.")
    @DecimalMax(value = "20.0", message = "Desconto máximo é 20%.")
    private BigDecimal percentual;
}

