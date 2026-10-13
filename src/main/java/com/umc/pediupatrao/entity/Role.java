package com.umc.pediupatrao.entity;

public enum Role {
    ADMIN,
    GERENTE,
    ATENDENTE;

    public String authority() {
        return "ROLE_" + name();
    }

    public static Role of(String raw) {
        if (raw == null) throw new IllegalArgumentException("Perfil é obrigatório (ADMIN, GERENTE ou ATENDENTE).");
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Perfil inválido: " + raw + ". Use ADMIN, GERENTE ou ATENDENTE.");
        }
    }
}

