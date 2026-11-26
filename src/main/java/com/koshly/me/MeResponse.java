package com.koshly.me;

public record MeResponse(
        String uid,
        String email,
        boolean emailVerified,
        String signInProvider) {
}