package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    /**
     * Pedidos cuja entrada ocorreu no intervalo informado (inclusivo).
     * Usado pelo dashboard para limitar a consulta no MongoDB.
     */
    List<Pedido> findByDataEntradaBetween(LocalDateTime inicio, LocalDateTime fim);

    Optional<Pedido> findFirstByOrderByDataEntradaAsc();

    Optional<Pedido> findFirstByOrderByDataEntradaDesc();
}

