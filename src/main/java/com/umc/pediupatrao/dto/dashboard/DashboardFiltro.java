package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;

import com.umc.pediupatrao.entity.StatusPedido;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parâmetros de consulta compartilhados pelos endpoints do dashboard.
 *
 * <p>Além dos filtros, o objeto concentra a conversão/validação dos valores
 * recebidos na URL. Datas usam o formato ISO ({@code yyyy-MM-dd}) e o filtro
 * de status aceita uma lista separada por vírgula
 * ({@code status=FINALIZADO,CANCELADO}).</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class DashboardFiltro {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataInicio;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataFim;

    /** Lista separada por vírgula com os status que devem ser considerados. */
    private String status;

    private Agrupamento agrupamento = Agrupamento.DIA;

    private Integer limite = 10;

    /**
     * Converte o filtro textual de status para o enum da aplicação.
     *
     * @throws IllegalArgumentException quando algum status informado não existe.
     */
    public Set<StatusPedido> statusSelecionados() {
        if (status == null || status.isBlank()) {
            return Set.of();
        }
        Set<StatusPedido> selecionados = EnumSet.noneOf(StatusPedido.class);
        for (String valor : status.split(",")) {
            String normalizado = valor.trim().toUpperCase();
            if (normalizado.isEmpty()) {
                continue;
            }
            try {
                selecionados.add(StatusPedido.valueOf(normalizado));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Status inválido: '" + valor.trim() + "'. Valores aceitos: "
                                + java.util.Arrays.toString(StatusPedido.values()));
            }
        }
        return selecionados;
    }

    public Agrupamento agrupamentoOuPadrao() {
        return agrupamento == null ? Agrupamento.DIA : agrupamento;
    }

    public int limiteOuPadrao(int padrao) {
        return limite == null ? padrao : limite;
    }
}
