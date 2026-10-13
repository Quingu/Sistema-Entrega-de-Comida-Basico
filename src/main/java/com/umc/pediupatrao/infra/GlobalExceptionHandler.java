package com.umc.pediupatrao.infra;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransicaoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> transicao(TransicaoInvalidaException e) {
        return erro(HttpStatus.UNPROCESSABLE_ENTITY, "Transição de status inválida.", e.getMessage());
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Map<String, Object>> negocio(NegocioException e) {
        return erro(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada.", e.getMessage());
    }

    @ExceptionHandler({ AccessDeniedException.class, org.springframework.security.authorization.AuthorizationDeniedException.class })
    public ResponseEntity<Map<String, Object>> negado(RuntimeException e) {
        return erro(HttpStatus.FORBIDDEN, "Acesso negado.", "Seu perfil não tem permissão para esta operação.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacao(MethodArgumentNotValidException e) {
        Map<String, String> campos = new HashMap<>();
        for (FieldError f : e.getBindingResult().getFieldErrors()) campos.put(f.getField(), f.getDefaultMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("erro", "Dados inválidos.");
        body.put("detalhes", campos);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> ilegal(IllegalArgumentException e) {
        return erro(HttpStatus.BAD_REQUEST, "Requisição inválida.", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> erro(HttpStatus status, String erro, String detalhe) {
        Map<String, Object> body = new HashMap<>();
        body.put("erro", erro);
        body.put("detalhe", detalhe);
        return ResponseEntity.status(status).body(body);
    }
}

