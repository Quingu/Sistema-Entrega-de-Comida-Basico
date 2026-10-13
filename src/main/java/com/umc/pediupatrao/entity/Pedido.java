package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class Pedido {
    @Id
    private String id;
    private String clienteId;
    private List<ItemPedido> itens = new ArrayList<>();

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal percentualDesconto = BigDecimal.ZERO;
    private BigDecimal valorDesconto = BigDecimal.ZERO;
    private BigDecimal totalFinal = BigDecimal.ZERO;

    private StatusPedido status = StatusPedido.RECEBIDO;

    private String usuarioResponsavel;
    private LocalDateTime dataEntrada;
    private LocalDateTime dataSaida;
    private String responsavelSaida;
    private String justificativaCancelamento;

    public void setItens(List<ItemPedido> itens) {
        this.itens = itens == null ? new ArrayList<>() : itens;
    }
}

