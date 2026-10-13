package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Document(collection = "auditorias")
@Getter
@Setter
@NoArgsConstructor
public class Auditoria {
    @Id
    private String id;
    private LocalDateTime dataHora;
    private String usuario;
    private String perfil;
    private String operacao;
    private String entidade;
    private String entidadeId;
    private String camposAlterados;
    private String valorAnterior;
    private String valorNovo;
    private String justificativa;
}

