package com.umc.pediupatrao.infra;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class TransicaoInvalidaException extends NegocioException {
    public TransicaoInvalidaException(String message) { super(message); }
}

