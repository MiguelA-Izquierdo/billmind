package dev.izquierdo.billmind._shared.domain.port;

public interface ExternalAuthPort {
    boolean isAuthorized(String bearerToken);
}