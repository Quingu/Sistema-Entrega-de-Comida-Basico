package com.umc.pediupatrao.config;

import java.math.BigDecimal;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.springframework.beans.factory.annotation.Value;

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

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtos) {
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

