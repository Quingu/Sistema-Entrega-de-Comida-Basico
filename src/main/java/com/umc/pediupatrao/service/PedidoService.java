package com.umc.pediupatrao.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.umc.pediupatrao.dto.CancelamentoDTO;
import com.umc.pediupatrao.dto.DescontoDTO;
import com.umc.pediupatrao.dto.ItemPedidoDTO;
import com.umc.pediupatrao.dto.PedidoCreateDTO;
import com.umc.pediupatrao.dto.StatusDTO;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.infra.NegocioException;
import com.umc.pediupatrao.infra.TransicaoInvalidaException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoria;

    private String atual() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "sistema";
        }
    }

    private void exigirGerente() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean ok = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE"));
        if (!ok) throw new AccessDeniedException("Operação exclusiva do perfil GERENTE.");
    }


    public Pedido criar(PedidoCreateDTO dto) {
        if (dto == null) {
            throw new NegocioException("Dados do pedido são obrigatórios.");
        }
        if (dto.getClienteId() == null || dto.getClienteId().isBlank()) {
            throw new NegocioException("Cliente é obrigatório.");
        }
        if (!clienteRepository.existsById(dto.getClienteId())) {
            throw new NegocioException("Cliente não encontrado: " + dto.getClienteId());
        }
        if (dto.getItens() == null || dto.getItens().isEmpty()) {
            throw new NegocioException("O pedido deve conter pelo menos um item.");
        }
        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedidoDTO i : dto.getItens()) {
            if (i == null) throw new NegocioException("Item do pedido inválido.");
            if (i.getProdutoId() == null || i.getProdutoId().isBlank()) {
                throw new NegocioException("Produto é obrigatório em todos os itens.");
            }
            Produto p = produtoRepository.findById(i.getProdutoId())
                    .orElseThrow(() -> new NegocioException("Produto inválido: " + i.getProdutoId()));
            if (Boolean.FALSE.equals(p.getAtivo())) {
                throw new NegocioException("Produto inativo: " + p.getNome());
            }
            if (i.getQuantidade() <= 0) throw new NegocioException("Quantidade deve ser ao menos 1.");
            BigDecimal preco = p.getPreco() == null ? BigDecimal.ZERO
                    : new BigDecimal(p.getPreco().toString()).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(p.getId(), p.getNome(), i.getQuantidade(), preco));
        }
        Pedido pedido = new Pedido();
        pedido.setClienteId(dto.getClienteId());
        pedido.setItens(itens);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setUsuarioResponsavel(atual());
        pedido.setDataEntrada(LocalDateTime.now());
        recalcular(pedido);
        Pedido salvo = pedidoRepository.save(pedido);
        auditoria.registrar("PEDIDO_CRIADO", "Pedido", salvo.getId(), "status,itens,subtotal,totalFinal",
                null, detalharPedido(salvo), null);
        return salvo;
    }

    public List<Pedido> listar() {
        return pedidoRepository.findAll();
    }

    public Pedido buscar(String id) {
        return pedidoRepository.findById(id).orElseThrow(() -> new NegocioException("Pedido não encontrado."));
    }


    @PreAuthorize("hasRole('GERENTE')")
    public Pedido transitar(String id, StatusDTO dto) {
        exigirGerente();
        if (dto == null || dto.getStatus() == null || dto.getStatus().isBlank()) {
            throw new TransicaoInvalidaException("O status do pedido é obrigatório.");
        }
        Pedido pedido = buscar(id);
        if (pedido.getStatus() == null || pedido.getStatus().isTerminal()) {
            throw new TransicaoInvalidaException("Pedido " + pedido.getStatus() + " não permite novas transições.");
        }
        StatusPedido destino;
        try {
            destino = StatusPedido.valueOf(dto.getStatus().trim().toUpperCase());
        } catch (Exception e) {
            throw new TransicaoInvalidaException("Status inválido: " + dto.getStatus());
        }
        if (destino == StatusPedido.CANCELADO) {
            throw new NegocioException("Use a operação de cancelamento com justificativa.");
        }
        if (!pedido.getStatus().podeTransitarPara(destino)) {
            throw new TransicaoInvalidaException(
                    "Transição bloqueada: " + pedido.getStatus() + " → " + destino
                    + ". Sequência: RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA/RETIRADO → FINALIZADO.");
        }
        StatusPedido antes = pedido.getStatus();
        pedido.setStatus(destino);
        if (destino == StatusPedido.SAIU_PARA_ENTREGA || destino == StatusPedido.RETIRADO) {
            if (pedido.getDataSaida() == null) pedido.setDataSaida(LocalDateTime.now());
            pedido.setResponsavelSaida(atual());
        }
        Pedido salvo = pedidoRepository.save(pedido);
        auditoria.registrar("STATUS_ALTERADO", "Pedido", id, "status", antes, destino,
                "saida=" + salvo.getDataSaida() + " resp=" + salvo.getResponsavelSaida());
        return salvo;
    }


    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, DescontoDTO dto) {
        exigirGerente();
        Pedido pedido = buscar(id);
        if (pedido.getStatus() == null || pedido.getStatus().isTerminal())
            throw new NegocioException("Pedido cancelado/finalizado não aceita desconto.");
        if (pedido.getStatus().jaSaiu()) throw new NegocioException("Desconto permitido somente antes da saída/retirada.");
        if (dto == null || dto.getPercentual() == null) throw new NegocioException("Percentual é obrigatório.");
        if (dto.getPercentual().compareTo(BigDecimal.ZERO) < 0
                || dto.getPercentual().compareTo(new BigDecimal("20.00")) > 0) {
            throw new NegocioException("Desconto deve estar entre 0 e 20%.");
        }
        BigDecimal antes = pedido.getTotalFinal();
        BigDecimal percAntes = pedido.getPercentualDesconto() == null ? BigDecimal.ZERO : pedido.getPercentualDesconto();
        pedido.setPercentualDesconto(dto.getPercentual().setScale(2, RoundingMode.HALF_UP));
        recalcular(pedido);
        Pedido salvo = pedidoRepository.save(pedido);
        auditoria.registrar("DESCONTO_APLICADO", "Pedido", id, "percentualDesconto,valorDesconto,totalFinal",
                "percentual=" + percAntes + "% total=" + antes,
                "percentual=" + salvo.getPercentualDesconto() + "% desconto=" + salvo.getValorDesconto()
                        + " total=" + salvo.getTotalFinal(),
                null);
        return salvo;
    }


    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, CancelamentoDTO dto) {
        exigirGerente();
        Pedido pedido = buscar(id);
        if (pedido.getStatus() == StatusPedido.CANCELADO) throw new NegocioException("Pedido já cancelado.");
        if (pedido.getStatus() == StatusPedido.FINALIZADO) throw new NegocioException("Pedido finalizado não pode ser cancelado.");
        if (pedido.getStatus() != null && pedido.getStatus().jaSaiu())
            throw new NegocioException("Cancelamento permitido somente antes da saída/retirada.");
        if (dto == null || dto.getJustificativa() == null || dto.getJustificativa().trim().length() < 5) {
            throw new NegocioException("Justificativa do cancelamento é obrigatória (mín. 5 caracteres).");
        }
        if (dto.getJustificativa().trim().length() > 500) {
            throw new NegocioException("Justificativa deve ter no máximo 500 caracteres.");
        }
        StatusPedido antes = pedido.getStatus();
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setJustificativaCancelamento(dto.getJustificativa().trim());
        Pedido salvo = pedidoRepository.save(pedido);
        auditoria.registrar("PEDIDO_CANCELADO", "Pedido", id, "status", antes, StatusPedido.CANCELADO,
                dto.getJustificativa().trim());
        return salvo;
    }

    public void recalcular(Pedido pedido) {
        BigDecimal sub = BigDecimal.ZERO;
        for (ItemPedido i : pedido.getItens()) {
            i.recalc();
            sub = sub.add(i.getSubtotal());
        }
        sub = sub.setScale(2, RoundingMode.HALF_UP);
        BigDecimal perc = pedido.getPercentualDesconto() == null ? BigDecimal.ZERO : pedido.getPercentualDesconto();
        BigDecimal desc = sub.multiply(perc).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        pedido.setSubtotal(sub);
        pedido.setValorDesconto(desc);
        pedido.setTotalFinal(sub.subtract(desc).setScale(2, RoundingMode.HALF_UP));
    }


    private String detalharPedido(Pedido p) {
        StringBuilder sb = new StringBuilder("status=").append(p.getStatus());
        sb.append(" itens=[");
        if (p.getItens() != null) {
            for (ItemPedido i : p.getItens()) {
                sb.append("{produto=").append(i.getProdutoId())
                        .append(" nome=").append(i.getNomeProduto())
                        .append(" qtd=").append(i.getQuantidade())
                        .append(" preco=").append(i.getPrecoUnitario())
                        .append(" subtotal=").append(i.getSubtotal())
                        .append("};");
            }
        }
        sb.append("] subtotal=").append(p.getSubtotal())
                .append(" desconto%=").append(p.getPercentualDesconto())
                .append(" descontoValor=").append(p.getValorDesconto())
                .append(" total=").append(p.getTotalFinal());
        return sb.toString();
    }
}

