package com.umc.pediupatrao.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Value("${app.demo-users.enabled:true}")
    private boolean demoUsersEnabled;

    @Value("${app.demo-users.admin-password:admin123}")
    private String adminPassword;

    @Value("${app.demo-users.gerente-password:gerente123}")
    private String gerentePassword;

    @Value("${app.demo-users.atendente-password:atendente123}")
    private String atendentePassword;

    /**
     * Cria clientes e pedidos de demonstração somente quando a coleção de
     * pedidos está vazia. Nunca sobrescreve dados reais já existentes.
     */
    @Value("${app.demo-data.enabled:true}")
    private boolean demoDataEnabled;

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo,
                                   ProdutoRepository produtos,
                                   ClienteRepository clientes,
                                   PedidoRepository pedidos) {
        return args -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            if (demoUsersEnabled) {
                criarSeAusente(repo, encoder, "admin", adminPassword, "ADMIN");
                criarSeAusente(repo, encoder, "gerente", gerentePassword, "GERENTE");
                criarSeAusente(repo, encoder, "atendente", atendentePassword, "ATENDENTE");
            }

            if (produtos.count() == 0) {
                criarProduto(produtos, "Pizza Calabresa", "Pizza", "49.90");
                criarProduto(produtos, "Pizza Marguerita", "Pizza", "44.90");
                criarProduto(produtos, "X-Burger", "Lanche", "24.90");
                criarProduto(produtos, "Refrigerante 2L", "Bebida", "12.00");
                criarProduto(produtos, "Porção de Batata", "Porção", "29.90");
                criarProduto(produtos, "Pizza Quatro Queijos", "Pizza", "54.90");
                criarProduto(produtos, "Suco Natural 500ml", "Bebida", "9.90");
            }

            if (demoDataEnabled) {
                criarClientesDemo(clientes);
                criarPedidosDemo(pedidos, produtos, clientes);
            }
        };
    }

    private void criarProduto(ProdutoRepository repo, String nome, String tipo, String preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(new BigDecimal(preco));
        p.setAtivo(true);
        repo.save(p);
        System.out.println("Produto demo criado: " + nome + " R$ " + preco);
    }

    private void criarCliente(ClienteRepository repo, String nome, String telefone, String bairro, String cidade) {
        Cliente c = new Cliente();
        c.setNome(nome);
        c.setTelefone(telefone);
        c.setBairro(bairro);
        c.setCidade(cidade);
        c.setEstado("SP");
        c.setEndereco("Rua das Pizzas, " + (100 + Math.abs(nome.hashCode()) % 800));
        repo.save(c);
    }

    private void criarClientesDemo(ClienteRepository repo) {
        if (repo.count() > 0) {
            return;
        }
        criarCliente(repo, "Ana Beatriz Souza", "(11) 98888-1010", "Centro", "São Paulo");
        criarCliente(repo, "Bruno Carvalho", "(11) 97777-2020", "Jardim Paulista", "São Paulo");
        criarCliente(repo, "Camila Ferreira", "(11) 96666-3030", "Vila Mariana", "São Paulo");
        criarCliente(repo, "Diego Martins", "(11) 95555-4040", "Moema", "São Paulo");
        criarCliente(repo, "Eduarda Lima", "(11) 94444-5050", "Tatuapé", "São Paulo");
        criarCliente(repo, "Felipe Nogueira", "(11) 93333-6060", "Santana", "São Paulo");
        criarCliente(repo, "Gabriela Rocha", "(11) 92222-7070", "Pinheiros", "São Paulo");
        criarCliente(repo, "Henrique Alves", "(11) 91111-8080", "Butantã", "São Paulo");
        System.out.println("Clientes demo criados: 8");
    }

    private void criarPedidosDemo(PedidoRepository pedidos,
                                  ProdutoRepository produtos,
                                  ClienteRepository clientes) {
        if (pedidos.count() > 0) {
            return;
        }
        List<Produto> disponiveis = produtos.findAll().stream()
                .filter(p -> p != null && p.getId() != null && Boolean.TRUE.equals(p.getAtivo()))
                .toList();
        List<Cliente> clientesDisponiveis = clientes.findAll();
        if (disponiveis.isEmpty() || clientesDisponiveis.isEmpty()) {
            return;
        }

        Random random = new Random(20261110L);
        LocalDate hoje = LocalDate.now();
        Map<StatusPedido, Integer> criadosPorStatus = new EnumMap<>(StatusPedido.class);

        for (int diasAtras = 59; diasAtras >= 0; diasAtras--) {
            int quantidadePedidos = 1 + (random.nextInt(10) < 4 ? 1 : 0);
            for (int n = 0; n < quantidadePedidos; n++) {
                StatusPedido status = sortearStatus(diasAtras, random);
                LocalDateTime entrada = hoje.minusDays(diasAtras)
                        .atTime(18 + random.nextInt(4), random.nextInt(60));
                Pedido pedido = montarPedido(random, entrada, status, disponiveis, clientesDisponiveis);
                pedidos.save(pedido);
                criadosPorStatus.merge(status, 1, Integer::sum);
            }
        }

        // Garante que todos os status apareçam nas evidências do dashboard.
        for (StatusPedido status : StatusPedido.values()) {
            if (criadosPorStatus.getOrDefault(status, 0) == 0) {
                LocalDateTime entrada = LocalDateTime.now().minusMinutes(15L * (status.ordinal() + 1));
                Pedido pedido = montarPedido(random, entrada, status, disponiveis, clientesDisponiveis);
                pedidos.save(pedido);
            }
        }

        System.out.println("Pedidos demo criados: " + pedidos.count());
    }

    private StatusPedido sortearStatus(int diasAtras, Random random) {
        int sorteio = random.nextInt(100);
        if (diasAtras == 0) {
            if (sorteio < 30) return StatusPedido.RECEBIDO;
            if (sorteio < 60) return StatusPedido.EM_PREPARACAO;
            if (sorteio < 80) return StatusPedido.PRONTO;
            return StatusPedido.SAIU_PARA_ENTREGA;
        }
        if (diasAtras <= 2) {
            if (sorteio < 20) return StatusPedido.EM_PREPARACAO;
            if (sorteio < 40) return StatusPedido.PRONTO;
            if (sorteio < 60) return StatusPedido.SAIU_PARA_ENTREGA;
            if (sorteio < 75) return StatusPedido.RETIRADO;
            if (sorteio < 90) return StatusPedido.FINALIZADO;
            return StatusPedido.CANCELADO;
        }
        if (diasAtras <= 6) {
            if (sorteio < 10) return StatusPedido.CANCELADO;
            if (sorteio < 20) return StatusPedido.RETIRADO;
            if (sorteio < 35) return StatusPedido.SAIU_PARA_ENTREGA;
            return StatusPedido.FINALIZADO;
        }
        if (sorteio < 8) return StatusPedido.CANCELADO;
        if (sorteio < 12) return StatusPedido.RETIRADO;
        return StatusPedido.FINALIZADO;
    }

    private Pedido montarPedido(Random random,
                                LocalDateTime entrada,
                                StatusPedido status,
                                List<Produto> disponiveis,
                                List<Cliente> clientes) {
        Pedido pedido = new Pedido();
        pedido.setClienteId(clientes.get(random.nextInt(clientes.size())).getId());
        pedido.setStatus(status);
        pedido.setUsuarioResponsavel(random.nextBoolean() ? "atendente" : "admin");
        pedido.setDataEntrada(entrada);

        List<Produto> sorteados = new ArrayList<>(disponiveis);
        Collections.shuffle(sorteados, random);
        int quantidadeItens = 1 + random.nextInt(Math.min(3, sorteados.size()));
        List<ItemPedido> itens = new ArrayList<>();
        for (int i = 0; i < quantidadeItens; i++) {
            Produto produto = sorteados.get(i);
            int quantidade = 1 + random.nextInt(3);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, produto.getPreco()));
        }
        pedido.setItens(itens);

        BigDecimal subtotal = itens.stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentual = status == StatusPedido.FINALIZADO && random.nextInt(10) < 3
                ? BigDecimal.valueOf(5L * (1 + random.nextInt(2)))
                : BigDecimal.ZERO;
        BigDecimal desconto = subtotal.multiply(percentual)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        pedido.setSubtotal(subtotal);
        pedido.setPercentualDesconto(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setTotalFinal(subtotal.subtract(desconto).setScale(2, RoundingMode.HALF_UP));

        if (status == StatusPedido.SAIU_PARA_ENTREGA
                || status == StatusPedido.RETIRADO
                || status == StatusPedido.FINALIZADO) {
            LocalDateTime agora = LocalDateTime.now();
            LocalDateTime saida = entrada.plusMinutes(20 + random.nextInt(45));
            if (saida.isAfter(agora)) {
                saida = entrada.isAfter(agora) ? entrada : agora.minusMinutes(5 + random.nextInt(20));
            }
            pedido.setDataSaida(saida);
            pedido.setResponsavelSaida("gerente");
        }
        if (status == StatusPedido.CANCELADO) {
            pedido.setJustificativaCancelamento("Pedido cancelado na demonstração: cliente solicitou o cancelamento.");
        }
        return pedido;
    }

    private void criarSeAusente(UsuarioRepository repo, BCryptPasswordEncoder encoder,
                                String username, String senha, String role) {
        if (repo.findByUsername(username).isEmpty()) {
            Usuario u = new Usuario();
            u.setUsername(username);
            u.setPassword(encoder.encode(senha));
            u.setRole(role);
            repo.save(u);
            System.out.println("Usuário demo criado: " + username + " / perfil " + role);
        } else {
            repo.findByUsername(username).ifPresent(u -> {
                if (u.getRole() == null || u.getRole().equalsIgnoreCase("USER")) {
                    u.setRole(role);
                    repo.save(u);
                }
            });
        }
    }
}
