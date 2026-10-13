package com.umc.pediupatrao.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.umc.pediupatrao.entity.Auditoria;

public interface AuditoriaRepository extends MongoRepository<Auditoria, String> {
    List<Auditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId);
    List<Auditoria> findAllByOrderByDataHoraDesc();
}

