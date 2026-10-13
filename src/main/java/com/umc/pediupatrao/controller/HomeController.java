package com.umc.pediupatrao.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.umc.pediupatrao.dto.CancelamentoDTO;
import com.umc.pediupatrao.dto.DescontoDTO;
import com.umc.pediupatrao.dto.PedidoCreateDTO;
import com.umc.pediupatrao.dto.StatusDTO;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    @GetMapping("/error")
    public String error() {
        return "error";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model, RedirectAttributes ra) {
        return usuarioService.buscarPorId(id).map(u -> {
            u.setPassword(null);
            model.addAttribute("usuario", u);
            model.addAttribute("content", "usuarios/form :: content");
            return "layout";
        }).orElseGet(() -> {
            ra.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario, RedirectAttributes ra) {
        try {
            usuarioService.salvarUsuario(usuario);
            ra.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id, @ModelAttribute Usuario usuario, RedirectAttributes ra) {
        try {
            usuarioService.atualizarUsuario(id, usuario);
            ra.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id, RedirectAttributes ra) {
        try {
            usuarioService.deletarUsuario(id);
            ra.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }


    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        List<Pedido> pedidos = pedidoService.listar();
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("content", "pedidos :: content");
        log.info("Carregando fragmento: pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE','ADMIN')")
    public String novoPedidoForm(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("pedidoCreate", new PedidoCreateDTO());
        model.addAttribute("content", "pedidos/novo :: content");
        return "layout";
    }

    @PostMapping("/pedidos/novo")
    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE','ADMIN')")
    public String criarPedido(@Valid @ModelAttribute PedidoCreateDTO pedidoCreate,
                              org.springframework.validation.BindingResult binding,
                              RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("erro", "Dados do pedido inválidos: "
                    + binding.getFieldErrors().stream()
                            .map(f -> f.getField() + " " + f.getDefaultMessage())
                            .findFirst().orElse("verifique os campos."));
            return "redirect:/pedidos/novo";
        }
        try {
            pedidoService.criar(pedidoCreate);
            ra.addFlashAttribute("sucesso", "Pedido criado! Total calculado no servidor.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos/novo";
        }
        return "redirect:/pedidos";
    }

    @GetMapping("/pedidos/{id}")
    public String detalhePedido(@PathVariable String id, Model model, RedirectAttributes ra) {
        try {
            model.addAttribute("pedido", pedidoService.buscar(id));
            model.addAttribute("historico", auditoriaService.porEntidade("Pedido", id));
            model.addAttribute("content", "pedidos/detalhe :: content");
            return "layout";
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
    }

    @PostMapping("/pedidos/{id}/status")
    @PreAuthorize("hasRole('GERENTE')")
    public String mudarStatus(@PathVariable String id, @Valid @ModelAttribute StatusDTO dto,
                              org.springframework.validation.BindingResult binding,
                              RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("erro", "Status inválido.");
            return "redirect:/pedidos/" + id;
        }
        try {
            pedidoService.transitar(id, dto);
            ra.addFlashAttribute("sucesso", "Status atualizado.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/desconto")
    @PreAuthorize("hasRole('GERENTE')")
    public String desconto(@PathVariable String id, @Valid @ModelAttribute DescontoDTO dto,
                           org.springframework.validation.BindingResult binding,
                           RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("erro", "Desconto inválido: deve estar entre 0 e 20%.");
            return "redirect:/pedidos/" + id;
        }
        try {
            pedidoService.aplicarDesconto(id, dto);
            ra.addFlashAttribute("sucesso", "Desconto aplicado e total recalculado no servidor.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/cancelar")
    @PreAuthorize("hasRole('GERENTE')")
    public String cancelar(@PathVariable String id, @Valid @ModelAttribute CancelamentoDTO dto,
                           org.springframework.validation.BindingResult binding,
                           RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("erro", "Justificativa inválida: deve ter entre 5 e 500 caracteres.");
            return "redirect:/pedidos/" + id;
        }
        try {
            pedidoService.cancelar(id, dto);
            ra.addFlashAttribute("sucesso", "Pedido cancelado (preservado para auditoria).");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }


    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }

    @PostMapping("/produtos/salvar")
    @PreAuthorize("hasRole('ADMIN')")
    public String salvarProduto(@ModelAttribute Produto produto, RedirectAttributes ra) {
        try {
            produtoService.salvar(produto);
            ra.addFlashAttribute("sucesso", "Produto salvo!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/produtos";
    }

    @PostMapping("/produtos/excluir/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String excluirProduto(@PathVariable String id, RedirectAttributes ra) {
        try {
            produtoService.excluir(id);
            ra.addFlashAttribute("sucesso", "Produto excluído!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/produtos";
    }


    @GetMapping("/clientes")
    public String clientes(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    @PreAuthorize("hasAnyRole('ATENDENTE','ADMIN')")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    @PreAuthorize("hasAnyRole('ATENDENTE','ADMIN')")
    public String salvarCliente(@ModelAttribute Cliente cliente, RedirectAttributes ra) {
        try {
            clienteService.salvar(cliente);
            ra.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/clientes/novo";
        }
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    @PreAuthorize("hasAnyRole('ATENDENTE','ADMIN')")
    public String editarClienteForm(@PathVariable String id, Model model, RedirectAttributes ra) {
        return clienteService.buscarPorId(id).map(c -> {
            model.addAttribute("cliente", c);
            model.addAttribute("content", "clientes/form :: content");
            return "layout";
        }).orElseGet(() -> {
            ra.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        });
    }

    @PostMapping("/clientes/editar/{id}")
    @PreAuthorize("hasAnyRole('ATENDENTE','ADMIN')")
    public String atualizarCliente(@PathVariable String id, @ModelAttribute Cliente cliente, RedirectAttributes ra) {
        try {
            cliente.setId(id);
            clienteService.salvar(cliente);
            ra.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/clientes/editar/" + id;
        }
        return "redirect:/clientes";
    }


    @GetMapping("/auditoria")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public String auditoria(Model model) {
        model.addAttribute("registros", auditoriaService.listarTodas());
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }

    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin() {
        return "admin";
    }
}

