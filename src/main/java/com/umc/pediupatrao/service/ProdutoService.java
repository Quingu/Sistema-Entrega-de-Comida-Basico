package com.umc.pediupatrao.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ProdutoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoria;

    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }


    @PreAuthorize("hasRole('ADMIN')")
    public Produto salvar(Produto produto) {
        validar(produto);
        if (produto.getPreco() != null) {
            produto.setPreco(produto.getPreco().setScale(2, RoundingMode.HALF_UP));
        }
        if (produto.getAtivo() == null) produto.setAtivo(true);
        boolean novo = produto.getId() == null;
        Produto salvo = produtoRepository.save(produto);
        auditoria.registrar(novo ? "PRODUTO_CRIADO" : "PRODUTO_ALTERADO", "Produto", salvo.getId(),
                "nome,preco,ativo", null, salvo.getNome() + " preco=" + salvo.getPreco(), null);
        return salvo;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void excluir(String id) {
        produtoRepository.deleteById(id);
        auditoria.registrar("PRODUTO_EXCLUIDO", "Produto", id, "todos", "id=" + id, null, null);
    }

    private void validar(Produto p) {
        if (p.getNome() == null || p.getNome().isBlank()) throw new IllegalArgumentException("Nome do produto é obrigatório.");
        if (p.getPreco() == null || p.getPreco().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Preço deve ser maior que zero. Use BigDecimal com 2 casas.");
        }
    }
}

