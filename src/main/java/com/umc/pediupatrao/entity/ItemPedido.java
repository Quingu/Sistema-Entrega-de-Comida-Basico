package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ItemPedido {
    private String produtoId;
    private String nomeProduto;
    private int quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;

    public ItemPedido(String produtoId, String nomeProduto, int quantidade, BigDecimal precoUnitario) {
        this.produtoId = produtoId;
        this.nomeProduto = nomeProduto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario == null ? BigDecimal.ZERO : precoUnitario.setScale(2, RoundingMode.HALF_UP);
        recalc();
    }

    public void recalc() {
        if (precoUnitario == null) precoUnitario = BigDecimal.ZERO;
        this.subtotal = precoUnitario.multiply(BigDecimal.valueOf(Math.max(quantidade, 0))).setScale(2, RoundingMode.HALF_UP);
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
        recalc();
    }

    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario == null ? BigDecimal.ZERO : precoUnitario.setScale(2, RoundingMode.HALF_UP);
        recalc();
    }
}

