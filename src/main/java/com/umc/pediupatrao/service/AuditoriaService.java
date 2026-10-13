package com.umc.pediupatrao.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository repository;

    public Auditoria registrar(String operacao, String entidade, String entidadeId,
                               String camposAlterados, Object valorAnterior, Object valorNovo,
                               String justificativa) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = auth != null ? auth.getName() : "sistema";
        String perfil = "SISTEMA";
        if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            perfil = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        }
        Auditoria a = new Auditoria();
        a.setDataHora(LocalDateTime.now());
        a.setUsuario(sanitize(usuario));
        a.setPerfil(perfil);
        a.setOperacao(operacao);
        a.setEntidade(entidade);
        a.setEntidadeId(entidadeId);
        a.setCamposAlterados(sanitize(camposAlterados));
        a.setValorAnterior(sanitize(truncate(String.valueOf(valorAnterior))));
        a.setValorNovo(sanitize(truncate(String.valueOf(valorNovo))));
        a.setJustificativa(sanitize(justificativa));
        return repository.save(a);
    }

    public List<Auditoria> listarTodas() {
        return repository.findAllByOrderByDataHoraDesc();
    }

    public List<Auditoria> porEntidade(String entidade, String id) {
        return repository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade, id);
    }


    private String sanitize(String v) {
        if (v == null || v.equals("null")) return null;
        String low = v.toLowerCase();
        if (low.contains("password") || low.contains("senha") || low.contains("token")
                || low.contains("credential") || low.contains("credencial")
                || low.contains("secret") || low.contains("segredo")
                || low.contains("pwd") || low.contains("passw")) {
            return "[OCULTO]";
        }
        return v;
    }

    private String truncate(String v) {
        if (v == null) return null;
        return v.length() > 2000 ? v.substring(0, 2000) : v;
    }
}

