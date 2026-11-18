package com.secmulher.modules.core.domain.services;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.dtos.UserDtos.UserRequest;
import com.secmulher.modules.core.domain.dtos.UserDtos.UserResponse;
import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.repositories.UserRepository;

// Gestão de usuários é exclusiva de administradores; cada usuário pode consultar o próprio cadastro
@Service
public class UserService {
    final UserRepository userRepository;
    final PasswordEncoder passwordEncoder;
    final AccessControlService accessControl;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AccessControlService accessControl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessControl = accessControl;
    }

    public boolean isEmailRegistered(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        accessControl.requireAdmin();
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        accessControl.requireAdminOrSelf(id);
        return UserResponse.from(getUserById(id));
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        accessControl.requireAdmin();
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessException("A senha é obrigatória na criação do usuário");
        }
        if (isEmailRegistered(request.email())) {
            throw new ConflictException("E-mail já cadastrado: " + request.email());
        }

        User user = User.builder()
            .name(request.name())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .isGlobalAdmin(Objects.requireNonNullElse(request.isGlobalAdmin(), false))
            .isActive(Objects.requireNonNullElse(request.isActive(), true))
            .build();

        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        accessControl.requireAdmin();
        User user = getUserById(id);
        boolean self = accessControl.requireUser().getId().equals(id);

        if (!user.getEmail().equals(request.email()) && isEmailRegistered(request.email())) {
            throw new ConflictException("E-mail já cadastrado: " + request.email());
        }
        // Evita que o administrador se tranque para fora do sistema
        if (self && (Boolean.FALSE.equals(request.isGlobalAdmin()) || Boolean.FALSE.equals(request.isActive()))) {
            throw new BusinessException("Você não pode remover o próprio acesso de administrador nem se desativar");
        }

        user.setName(request.name());
        user.setEmail(request.email());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.isGlobalAdmin() != null) {
            user.setIsGlobalAdmin(request.isGlobalAdmin());
        }
        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }

        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void delete(UUID id) {
        accessControl.requireAdmin();
        if (accessControl.requireUser().getId().equals(id)) {
            throw new BusinessException("Você não pode excluir o próprio usuário");
        }
        userRepository.delete(getUserById(id));
    }
}
