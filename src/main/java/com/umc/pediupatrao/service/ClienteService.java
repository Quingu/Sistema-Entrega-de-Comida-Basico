package com.umc.pediupatrao.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoria;

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }


    @PreAuthorize("hasAnyRole('ATENDENTE','ADMIN')")
    public Cliente salvar(Cliente cliente) {
        if (cliente.getId() == null) {
            validar(cliente);
            Cliente salvo = clienteRepository.save(cliente);
            auditoria.registrar("CLIENTE_CRIADO", "Cliente", salvo.getId(), "nome,telefone,endereco",
                    null, resumo(salvo), null);
            return salvo;
        }
        Cliente antes = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para atualização."));
        validar(cliente);
        Cliente salvo = clienteRepository.save(cliente);


        List<String> mudou = new ArrayList<>();
        List<String> valoresAntes = new ArrayList<>();
        List<String> valoresNovos = new ArrayList<>();
        comparar(mudou, valoresAntes, valoresNovos, "nome", antes.getNome(), salvo.getNome());
        comparar(mudou, valoresAntes, valoresNovos, "telefone", antes.getTelefone(), salvo.getTelefone());
        comparar(mudou, valoresAntes, valoresNovos, "logradouro", antes.getLogradouro(), salvo.getLogradouro());
        comparar(mudou, valoresAntes, valoresNovos, "numero", antes.getNumero(), salvo.getNumero());
        comparar(mudou, valoresAntes, valoresNovos, "complemento", antes.getComplemento(), salvo.getComplemento());
        comparar(mudou, valoresAntes, valoresNovos, "bairro", antes.getBairro(), salvo.getBairro());
        comparar(mudou, valoresAntes, valoresNovos, "cidade", antes.getCidade(), salvo.getCidade());
        comparar(mudou, valoresAntes, valoresNovos, "estado", antes.getEstado(), salvo.getEstado());
        comparar(mudou, valoresAntes, valoresNovos, "cep", antes.getCep(), salvo.getCep());
        comparar(mudou, valoresAntes, valoresNovos, "endereco", antes.getEndereco(), salvo.getEndereco());
        if (!mudou.isEmpty()) {
            auditoria.registrar("CLIENTE_ALTERADO", "Cliente", salvo.getId(), String.join(",", mudou),
                    String.join(" | ", valoresAntes),
                    String.join(" | ", valoresNovos), null);
        }
        return salvo;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void excluir(String id) {
        Cliente antes = clienteRepository.findById(id).orElse(null);
        clienteRepository.deleteById(id);
        if (antes != null) {
            auditoria.registrar("CLIENTE_EXCLUIDO", "Cliente", id, "todos", resumo(antes), null, null);
        }
    }

    private void validar(Cliente c) {
        if (c.getNome() == null || c.getNome().isBlank()) throw new IllegalArgumentException("Nome do cliente é obrigatório.");
        if (c.getTelefone() == null || c.getTelefone().isBlank()) throw new IllegalArgumentException("Telefone é obrigatório.");
    }

    private boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private void comparar(List<String> mudou, List<String> antes, List<String> novos,
                          String campo, Object valorAntes, Object valorNovo) {
        if (!eq(valorAntes, valorNovo)) {
            mudou.add(campo);
            antes.add(campo + "=" + valorAntes);
            novos.add(campo + "=" + valorNovo);
        }
    }

    private String resumoEndereco(Cliente c) {
        return (nvl(c.getLogradouro()) + " " + nvl(c.getNumero()) + " " + nvl(c.getBairro()) + " "
                + nvl(c.getCidade()) + "/" + nvl(c.getEstado()) + " CEP:" + nvl(c.getCep())).trim();
    }

    private String resumo(Cliente c) {
        return "nome=" + c.getNome() + " tel=" + c.getTelefone() + " end=[" + resumoEndereco(c) + "]";
    }

    private String nvl(String s) { return s == null ? "" : s; }
}

