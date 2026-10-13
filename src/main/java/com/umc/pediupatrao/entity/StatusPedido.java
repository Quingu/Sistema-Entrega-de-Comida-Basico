package com.umc.pediupatrao.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public enum StatusPedido {
    RECEBIDO,
    EM_PREPARACAO,
    PRONTO,
    SAIU_PARA_ENTREGA,
    RETIRADO,
    FINALIZADO,
    CANCELADO;

    private static final Map<StatusPedido, Set<StatusPedido>> TRANSICOES = new EnumMap<>(StatusPedido.class);

    static {
        TRANSICOES.put(RECEBIDO, EnumSet.of(EM_PREPARACAO, CANCELADO));
        TRANSICOES.put(EM_PREPARACAO, EnumSet.of(PRONTO, CANCELADO));
        TRANSICOES.put(PRONTO, EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO, CANCELADO));
        TRANSICOES.put(SAIU_PARA_ENTREGA, EnumSet.of(FINALIZADO));
        TRANSICOES.put(RETIRADO, EnumSet.of(FINALIZADO));
        TRANSICOES.put(FINALIZADO, EnumSet.noneOf(StatusPedido.class));
        TRANSICOES.put(CANCELADO, EnumSet.noneOf(StatusPedido.class));
    }

    public boolean podeTransitarPara(StatusPedido destino) {
        if (destino == null) return false;
        return TRANSICOES.getOrDefault(this, Set.of()).contains(destino);
    }


    public boolean jaSaiu() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO || this == FINALIZADO;
    }

    public boolean isTerminal() {
        return this == FINALIZADO || this == CANCELADO;
    }
}

