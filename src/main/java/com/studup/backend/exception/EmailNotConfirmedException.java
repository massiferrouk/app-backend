package com.studup.backend.exception;

/// Tentative de login avec un compte dont l'email n'est pas confirmé → HTTP 401
public class EmailNotConfirmedException extends RuntimeException {

    public EmailNotConfirmedException() {
        super("Confirme ton adresse email avant de te connecter");
    }
}
