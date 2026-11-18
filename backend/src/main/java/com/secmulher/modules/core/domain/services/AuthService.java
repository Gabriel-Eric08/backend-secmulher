package com.secmulher.modules.core.domain.services;

import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.dtos.ActivityTypeDtos.ActivityTypeSummary;
import com.secmulher.modules.core.domain.dtos.AuthDtos.CategoryAccessResponse;
import com.secmulher.modules.core.domain.dtos.AuthDtos.ChangePasswordRequest;
import com.secmulher.modules.core.domain.dtos.AuthDtos.LoginRequest;
import com.secmulher.modules.core.domain.dtos.AuthDtos.LoginResponse;
import com.secmulher.modules.core.domain.dtos.AuthDtos.MeResponse;
import com.secmulher.modules.core.domain.dtos.UserDtos.UserResponse;
import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.UnauthorizedException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;
import com.secmulher.modules.core.infra.repositories.CategoryRepository;
import com.secmulher.modules.core.infra.repositories.PermissionRepository;
import com.secmulher.modules.core.infra.repositories.UserRepository;
import com.secmulher.modules.core.infra.security.TokenService;
import com.secmulher.modules.core.infra.security.TokenService.IssuedToken;

@Service
public class AuthService {
    private static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos";

    final UserRepository userRepository;
    final PermissionRepository permissionRepository;
    final CategoryRepository categoryRepository;
    final PasswordEncoder passwordEncoder;
    final TokenService tokenService;
    final AccessControlService accessControl;
    final ActivityTypeService activityTypeService;
    // Hash usado quando o e-mail não existe, para o tempo de resposta não revelar quais e-mails estão cadastrados
    final String dummyHash;

    public AuthService(UserRepository userRepository, PermissionRepository permissionRepository,
            CategoryRepository categoryRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
            AccessControlService accessControl, ActivityTypeService activityTypeService) {
        this.activityTypeService = activityTypeService;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.accessControl = accessControl;
        this.dummyHash = passwordEncoder.encode("senha-inexistente");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(request.password(),
            user != null ? user.getPasswordHash() : dummyHash);

        if (user == null || !passwordMatches || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        IssuedToken token = tokenService.issue(user);
        return new LoginResponse(token.token(), "Bearer", token.expiresAt(), UserResponse.from(user));
    }

    // Administradores recebem acesso total a todas as categorias; os demais, a própria matriz
    @Transactional(readOnly = true)
    public MeResponse me() {
        User user = accessControl.requireUser();

        List<CategoryAccessResponse> access = Boolean.TRUE.equals(user.getIsGlobalAdmin())
            ? categoryRepository.findAll().stream()
                .map(category -> new CategoryAccessResponse(category.getId(), category.getName(),
                    category.getEntityType(), category.getTableReference(), true, true, true, true,
                    Map.of(), ConfidentialityLevel.CONFIDENTIAL,
                    registrableTypes(CategoryAccess.full(category.getId()))))
                .toList()
            : permissionRepository.findByUserId(user.getId()).stream()
                .map(permission -> new CategoryAccessResponse(permission.getCategory().getId(),
                    permission.getCategory().getName(), permission.getCategory().getEntityType(),
                    permission.getCategory().getTableReference(),
                    Boolean.TRUE.equals(permission.getCanRead()), Boolean.TRUE.equals(permission.getCanCreate()),
                    Boolean.TRUE.equals(permission.getCanUpdate()), Boolean.TRUE.equals(permission.getCanDelete()),
                    permission.getAllowedColumns() != null ? permission.getAllowedColumns() : Map.of(),
                    permission.getMaxConfidentiality(),
                    registrableTypes(CategoryAccess.from(permission))))
                .toList();

        return new MeResponse(UserResponse.from(user), access);
    }

    // Tipos ativos da categoria que o usuário pode registrar (exige permissão de edição)
    private List<ActivityTypeSummary> registrableTypes(CategoryAccess access) {
        if (!access.canUpdate()) {
            return List.of();
        }
        return activityTypeService.getActiveTypes(access.categoryId()).stream()
            .filter(type -> access.canRegisterActivityType(type.getKey()))
            .map(ActivityTypeSummary::from)
            .toList();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = accessControl.requireUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Senha atual incorreta");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
