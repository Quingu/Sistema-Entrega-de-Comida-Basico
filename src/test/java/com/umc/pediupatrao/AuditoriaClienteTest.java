package com.umc.pediupatrao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;

import static org.junit.jupiter.api.Assertions.*;

class AuditoriaClienteTest {

    @SuppressWarnings("unchecked")
    static <T> T fake(Class<T> iface, InvocationHandler h) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h);
    }

    @Test
    void alteracaoDeTelefoneEEnderecoGeraAuditoria() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("atendente", "x",
                        List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));
        Map<String, Cliente> base = new HashMap<>();
        Cliente antes = new Cliente();
        antes.setId("c1"); antes.setNome("Joao"); antes.setTelefone("(11) 1111-1111");
        antes.setLogradouro("Rua A"); antes.setNumero("10"); antes.setCidade("SP"); antes.setEstado("SP");
        base.put("c1", antes);

        ClienteRepository repo = fake(ClienteRepository.class, (proxy, m, a) -> {
            switch (m.getName()) {
                case "findById": return Optional.ofNullable(base.get((String) a[0]));
                case "save": { Cliente c = (Cliente) a[0]; base.put(c.getId(), c); return c; }
                default: return null;
            }
        });
        AtomicReference<String> operacao = new AtomicReference<>();
        AtomicReference<String> campos = new AtomicReference<>();
        AuditoriaService aud = new AuditoriaService(null) {
            @Override
            public Auditoria registrar(String o, String e, String id, String c, Object ant, Object novo, String j) {
                operacao.set(o); campos.set(c); return null;
            }
        };

        ClienteService service = new ClienteService(repo, aud);
        Cliente novo = new Cliente();
        novo.setId("c1"); novo.setNome("Joao"); novo.setTelefone("(11) 99999-9999");
        novo.setLogradouro("Rua B"); novo.setNumero("20"); novo.setCidade("SP"); novo.setEstado("SP");
        service.salvar(novo);

        assertEquals("CLIENTE_ALTERADO", operacao.get());
        assertTrue(campos.get().contains("telefone"), "deve auditar telefone, foi: " + campos.get());
    }

    @Test
    void auditoriaNuncaRegistraSenha() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "x",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        AtomicReference<Auditoria> guardada = new AtomicReference<>();
        AuditoriaRepository repo = fake(AuditoriaRepository.class, (proxy, m, a) -> {
            if (m.getName().equals("save")) { guardada.set((Auditoria) a[0]); return a[0]; }
            return null;
        });
        AuditoriaService real = new AuditoriaService(repo);
        real.registrar("TESTE", "Usuario", "1", "password=xxx", "password=123456", "password=abcdef", null);
        String tudo = "" + guardada.get().getValorNovo() + guardada.get().getValorAnterior() + guardada.get().getCamposAlterados();
        assertFalse(tudo.contains("123456"), "senha vazou para auditoria: " + tudo);
    }

    @Test
    void alteracaoSomenteDoNomeGeraAuditoriaComAntesEDepois() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("atendente", "x",
                        List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));
        Map<String, Cliente> base = new HashMap<>();
        Cliente antes = new Cliente();
        antes.setId("c1"); antes.setNome("Joao"); antes.setTelefone("(11) 1111-1111");
        base.put("c1", antes);
        ClienteRepository repo = fake(ClienteRepository.class, (proxy, m, a) -> {
            switch (m.getName()) {
                case "findById": return Optional.ofNullable(base.get((String) a[0]));
                case "save": { Cliente c = (Cliente) a[0]; base.put(c.getId(), c); return c; }
                default: return null;
            }
        });
        AtomicReference<String> campos = new AtomicReference<>();
        AtomicReference<Object> ant = new AtomicReference<>();
        AtomicReference<Object> novo = new AtomicReference<>();
        AuditoriaService aud = new AuditoriaService(null) {
            @Override
            public Auditoria registrar(String o, String e, String id, String c, Object a, Object n, String j) {
                campos.set(c); ant.set(a); novo.set(n); return null;
            }
        };
        ClienteService service = new ClienteService(repo, aud);
        Cliente atualizado = new Cliente();
        atualizado.setId("c1"); atualizado.setNome("Joao Silva"); atualizado.setTelefone("(11) 1111-1111");
        service.salvar(atualizado);
        assertNotNull(campos.get(), "alteração de nome deve gerar auditoria");
        assertTrue(campos.get().contains("nome"), "deve conter 'nome', foi: " + campos.get());
        assertTrue(String.valueOf(ant.get()).contains("Joao"), "valor anterior ausente: " + ant.get());
        assertTrue(String.valueOf(novo.get()).contains("Joao Silva"), "valor novo ausente: " + novo.get());
    }

    @Test
    void alteracaoSomenteDoComplementoGeraAuditoria() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("atendente", "x",
                        List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));
        Map<String, Cliente> base = new HashMap<>();
        Cliente antes = new Cliente();
        antes.setId("c1"); antes.setNome("Maria"); antes.setTelefone("(11) 2222-2222");
        antes.setLogradouro("Rua A"); antes.setNumero("10"); antes.setComplemento("Casa");
        antes.setBairro("Centro"); antes.setCidade("SP"); antes.setEstado("SP"); antes.setCep("01000-000");
        base.put("c1", antes);
        ClienteRepository repo = fake(ClienteRepository.class, (proxy, m, a) -> {
            switch (m.getName()) {
                case "findById": return Optional.ofNullable(base.get((String) a[0]));
                case "save": { Cliente c = (Cliente) a[0]; base.put(c.getId(), c); return c; }
                default: return null;
            }
        });
        AtomicReference<String> campos = new AtomicReference<>();
        AuditoriaService aud = new AuditoriaService(null) {
            @Override
            public Auditoria registrar(String o, String e, String id, String c, Object a, Object n, String j) {
                campos.set(c); return null;
            }
        };
        ClienteService service = new ClienteService(repo, aud);
        Cliente atualizado = new Cliente();
        atualizado.setId("c1"); atualizado.setNome("Maria"); atualizado.setTelefone("(11) 2222-2222");
        atualizado.setLogradouro("Rua A"); atualizado.setNumero("10"); atualizado.setComplemento("Apto 101");
        atualizado.setBairro("Centro"); atualizado.setCidade("SP"); atualizado.setEstado("SP"); atualizado.setCep("01000-000");
        service.salvar(atualizado);
        assertNotNull(campos.get(), "alteração de complemento deve gerar auditoria");
        assertTrue(campos.get().contains("complemento"), "deve conter 'complemento', foi: " + campos.get());
    }
}

