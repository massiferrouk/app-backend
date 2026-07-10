package com.studup.backend.exception;

/// Token de confirmation email invalide, expiré ou déjà utilisé → HTTP 400
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
