package com.secmulher.modules.core.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.repositories.UserRepository;

// Cria o primeiro administrador quando o banco não tem nenhum usuário (ADMIN_EMAIL/ADMIN_PASSWORD).
// Sem isso, ninguém conseguiria entrar, já que criar usuários exige ser administrador.
// Roda antes do seed de demonstração, que também cria usuários.
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    final UserRepository userRepository;
    final PasswordEncoder passwordEncoder;
    final String name;
    final String email;
    final String password;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-name:Administrador}") String name,
            @Value("${app.bootstrap.admin-email:}") String email,
            @Value("${app.bootstrap.admin-password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("Nenhum usuário cadastrado. Defina ADMIN_EMAIL e ADMIN_PASSWORD para criar o primeiro administrador.");
            return;
        }

        userRepository.save(User.builder()
            .name(name)
            .email(email)
            .passwordHash(passwordEncoder.encode(password))
            .isGlobalAdmin(true)
            .isActive(true)
            .build());
        log.info("Administrador inicial criado: {}", email);
    }
}
