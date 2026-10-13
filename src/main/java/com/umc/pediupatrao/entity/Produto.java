package com.umc.pediupatrao.entity;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "produtos")
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Produto {

    @Id
    private String id;
    private String nome;
    private String tipo;
    private BigDecimal preco;
    private Boolean ativo;
}

