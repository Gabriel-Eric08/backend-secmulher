package com.secmulher.modules.core.infra.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.secmulher.modules.core.domain.exceptions.UnauthorizedException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.repositories.UserRepository;

// Resolve o usuário autenticado a partir do JWT validado pelo Spring Security
@Component
public class CurrentUserProvider {
    final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Optional.empty();
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwtAuthentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new UnauthorizedException("Token inválido");
        }

        // Usuário excluído ou desativado perde o acesso imediatamente, mesmo com token válido
        return Optional.of(userRepository.findById(userId)
            .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
            .orElseThrow(() -> new UnauthorizedException("Usuário inexistente ou inativo")));
    }
}
