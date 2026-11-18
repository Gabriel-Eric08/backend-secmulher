package com.secmulher.modules.core.infra.seed;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.enums.RecordType;
import com.secmulher.modules.core.domain.services.ActivityService;
import com.secmulher.modules.core.infra.entities.ActivityLedger;
import com.secmulher.modules.core.infra.entities.Category;
import com.secmulher.modules.core.infra.entities.CategoryActivityType;
import com.secmulher.modules.core.infra.entities.CategoryField;
import com.secmulher.modules.core.infra.entities.Client;
import com.secmulher.modules.core.infra.entities.DynamicEntity;
import com.secmulher.modules.core.infra.entities.FileRecord;
import com.secmulher.modules.core.infra.entities.Permission;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.entities.enums.ActivityType;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;
import com.secmulher.modules.core.infra.entities.enums.FieldType;
import com.secmulher.modules.core.infra.repositories.ActivityLedgerRepository;
import com.secmulher.modules.core.infra.repositories.CategoryActivityTypeRepository;
import com.secmulher.modules.core.infra.repositories.CategoryFieldRepository;
import com.secmulher.modules.core.infra.repositories.CategoryRepository;
import com.secmulher.modules.core.infra.repositories.ClientRepository;
import com.secmulher.modules.core.infra.repositories.DynamicEntityRepository;
import com.secmulher.modules.core.infra.repositories.FileRecordRepository;
import com.secmulher.modules.core.infra.repositories.PermissionRepository;
import com.secmulher.modules.core.infra.repositories.UserRepository;
import com.secmulher.modules.core.infra.storage.FileStorage;

// Dados fictícios para demonstração: funcionárias com permissões diferentes, mulheres atendidas
// com histórico completo (chegada, triagem, atendimentos, cursos) e PDFs em branco simulando documentos.
// Uso: ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed   (não roda de novo se já tiver sido aplicado)
@Component
@Profile("seed")
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String CLIENTS_CATEGORY = "Mulheres Atendidas";
    private static final String COURSES_CATEGORY = "Inscrições em Cursos";
    private static final String LAWYER = "Dra. Marina Costa (advogada parceira)";
    private static final List<String> COURSES = List.of("Informática Básica", "Corte e Costura",
        "Empreendedorismo Feminino", "Gastronomia", "Manicure e Design de Sobrancelhas");

    enum Role { RECEPTION, SOCIAL, PSYCHOLOGY, INSTRUCTOR, LEGAL }

    enum Effect { NONE, MARK_RISK, ENROLL, COMPLETE_COURSE }

    record Step(int day, ActivityType type, Role role, String description, String fileName,
            ConfidentialityLevel level, Effect effect) {}

    record Woman(String nome, LocalDate nascimento, String telefone, int daysAgo, String curso) {}

    record Staff(User coordinator, User psychologist, User socialWorker, User receptionist, User instructor) {
        User forRole(Role role) {
            return switch (role) {
                case RECEPTION -> receptionist;
                case SOCIAL, LEGAL -> socialWorker;
                case PSYCHOLOGY -> psychologist;
                case INSTRUCTOR -> instructor;
            };
        }
    }

    private static Step step(int day, ActivityType type, Role role, String description) {
        return new Step(day, type, role, description, null, null, Effect.NONE);
    }

    private static Step step(int day, ActivityType type, Role role, String description, Effect effect) {
        return new Step(day, type, role, description, null, null, effect);
    }

    private static Step step(int day, ActivityType type, Role role, String description, String fileName,
            ConfidentialityLevel level) {
        return new Step(day, type, role, description, fileName, level, Effect.NONE);
    }

    // Trajetórias típicas de atendimento; {curso} é substituído pelo curso de cada mulher
    private static final List<List<Step>> JOURNEYS = List.of(
        // Violência doméstica
        List.of(
            step(0, ActivityType.NOTE, Role.RECEPTION, "Chegada ao centro encaminhada pela Delegacia da Mulher (DEAM). Acolhida pela recepção e conduzida à sala de escuta.",
                "Boletim de ocorrência.pdf", ConfidentialityLevel.CONFIDENTIAL),
            step(0, ActivityType.TRIAGE, Role.SOCIAL, "Triagem: relata violência psicológica e física praticada pelo companheiro há cerca de dois anos. Avaliada como situação de risco.",
                "Ficha de triagem.pdf", ConfidentialityLevel.RESTRICTED),
            step(0, ActivityType.NOTE, Role.SOCIAL, "Situação de risco identificada na triagem.", Effect.MARK_RISK),
            step(3, ActivityType.SERVICE, Role.PSYCHOLOGY, "Atendimento psicológico (1ª sessão): escuta qualificada e construção de vínculo. Apresenta sinais de ansiedade."),
            step(7, ActivityType.SERVICE, Role.LEGAL, "Orientação jurídica: explicados os direitos previstos na Lei Maria da Penha e solicitada medida protetiva de urgência.",
                "Pedido de medida protetiva.pdf", ConfidentialityLevel.CONFIDENTIAL),
            step(10, ActivityType.SERVICE, Role.PSYCHOLOGY, "Atendimento psicológico (2ª sessão): trabalho de fortalecimento da autoestima e identificação da rede de apoio."),
            step(17, ActivityType.SERVICE, Role.PSYCHOLOGY, "Atendimento psicológico (3ª sessão): elaboração do plano de segurança pessoal.",
                "Laudo psicológico.pdf", ConfidentialityLevel.CONFIDENTIAL),
            step(24, ActivityType.FOLLOW_UP, Role.SOCIAL, "Visita domiciliar de acompanhamento. Medida protetiva deferida; agressor afastado do lar."),
            step(45, ActivityType.FOLLOW_UP, Role.PSYCHOLOGY, "Retorno: relata melhora do sono e redução dos sintomas de ansiedade. Mantido acompanhamento quinzenal.")
        ),
        // Autonomia econômica
        List.of(
            step(0, ActivityType.NOTE, Role.RECEPTION, "Chegada espontânea ao centro em busca de cursos de qualificação profissional.",
                "Termo de acolhimento.pdf", ConfidentialityLevel.INTERNAL),
            step(1, ActivityType.TRIAGE, Role.SOCIAL, "Triagem socioeconômica: desempregada, chefe de família com dois filhos. Interesse em qualificação para geração de renda.",
                "Comprovante de residência.pdf", ConfidentialityLevel.RESTRICTED),
            step(5, ActivityType.SERVICE, Role.INSTRUCTOR, "Inscrição no curso de {curso}.", Effect.ENROLL),
            step(12, ActivityType.SERVICE, Role.INSTRUCTOR, "Curso de {curso}: participação no módulo 1, frequência registrada."),
            step(26, ActivityType.SERVICE, Role.INSTRUCTOR, "Curso de {curso}: participação no módulo 2 e atividade prática avaliada."),
            step(40, ActivityType.SERVICE, Role.INSTRUCTOR, "Conclusão do curso de {curso} com aproveitamento.", Effect.COMPLETE_COURSE),
            step(40, ActivityType.NOTE, Role.INSTRUCTOR, "Certificado de conclusão emitido.",
                "Certificado - {curso}.pdf", ConfidentialityLevel.INTERNAL),
            step(50, ActivityType.FOLLOW_UP, Role.SOCIAL, "Encaminhada ao SINE e ao programa municipal de microcrédito para empreendedoras.")
        ),
        // Saúde mental
        List.of(
            step(0, ActivityType.NOTE, Role.RECEPTION, "Chegada ao centro encaminhada pela Unidade Básica de Saúde (UBS).",
                "Encaminhamento UBS.pdf", ConfidentialityLevel.RESTRICTED),
            step(0, ActivityType.TRIAGE, Role.SOCIAL, "Triagem psicossocial: relata tristeza persistente após separação e isolamento social.",
                "Ficha de triagem.pdf", ConfidentialityLevel.RESTRICTED),
            step(2, ActivityType.SERVICE, Role.PSYCHOLOGY, "Atendimento psicológico (1ª sessão): acolhimento e levantamento da história de vida."),
            step(9, ActivityType.SERVICE, Role.PSYCHOLOGY, "Atendimento psicológico (2ª sessão): trabalho sobre o luto da relação."),
            step(16, ActivityType.SERVICE, Role.PSYCHOLOGY, "Atendimento psicológico (3ª sessão): retomada de atividades de autocuidado."),
            step(23, ActivityType.SERVICE, Role.PSYCHOLOGY, "Atendimento psicológico (4ª sessão): avaliação da evolução do caso.",
                "Relatório psicológico.pdf", ConfidentialityLevel.CONFIDENTIAL),
            step(30, ActivityType.SERVICE, Role.PSYCHOLOGY, "Participação no grupo terapêutico de mulheres (encontro semanal)."),
            step(60, ActivityType.FOLLOW_UP, Role.PSYCHOLOGY, "Acompanhamento: boa adesão ao grupo terapêutico; sessões individuais passam a ser mensais.")
        ),
        // Orientação jurídica
        List.of(
            step(0, ActivityType.NOTE, Role.RECEPTION, "Chegada ao centro em busca de orientação sobre pensão alimentícia."),
            step(0, ActivityType.TRIAGE, Role.SOCIAL, "Triagem: demanda de pensão alimentícia e regularização da guarda dos filhos.",
                "Documentos pessoais (RG e CPF).pdf", ConfidentialityLevel.RESTRICTED),
            step(4, ActivityType.SERVICE, Role.LEGAL, "Orientação jurídica: levantamento da documentação e encaminhamento à Defensoria Pública.",
                "Procuração - Defensoria Pública.pdf", ConfidentialityLevel.RESTRICTED),
            step(20, ActivityType.FOLLOW_UP, Role.LEGAL, "Acompanhamento: ação de alimentos ajuizada pela Defensoria Pública.",
                "Petição inicial - ação de alimentos.pdf", ConfidentialityLevel.CONFIDENTIAL),
            step(35, ActivityType.FOLLOW_UP, Role.SOCIAL, "Contato telefônico: audiência de conciliação agendada; orientada sobre os próximos passos.")
        )
    );

    // Tipos de registro manual de cada categoria (os quatro padrão + os específicos do serviço)
    record SeedType(String key, String label, String color) {}

    private static final List<SeedType> CLIENT_ACTIVITY_TYPES = List.of(
        new SeedType("triagem", "Triagem", "#5b2a86"),
        new SeedType("atendimento", "Atendimento", "#2563eb"),
        new SeedType("acompanhamento", "Acompanhamento", "#0f766e"),
        new SeedType("anotacao", "Anotação", "#64748b"),
        new SeedType("atendimento_psicologico", "Atendimento psicológico", "#0e7490"),
        new SeedType("atendimento_juridico", "Atendimento jurídico", "#b45309"),
        new SeedType("visita_domiciliar", "Visita domiciliar", "#15803d"),
        new SeedType("atividade_curso", "Atividade de curso", "#7c3aed"));

    private static final List<SeedType> COURSE_ACTIVITY_TYPES = List.of(
        new SeedType("triagem", "Triagem", "#5b2a86"),
        new SeedType("atendimento", "Atendimento", "#2563eb"),
        new SeedType("acompanhamento", "Acompanhamento", "#0f766e"),
        new SeedType("anotacao", "Anotação", "#64748b"),
        new SeedType("matricula", "Matrícula", "#7c3aed"),
        new SeedType("presenca", "Presença", "#0891b2"),
        new SeedType("certificado", "Certificado", "#ca8a04"));

    private static final List<Woman> WOMEN = List.of(
        new Woman("Ana Paula Ferreira", LocalDate.of(1988, 3, 14), "(81) 98712-4433", 230, COURSES.get(0)),
        new Woman("Maria José da Silva", LocalDate.of(1975, 7, 2), "(81) 99654-2210", 215, COURSES.get(1)),
        new Woman("Joana Batista Lima", LocalDate.of(1992, 11, 23), "(81) 98833-1902", 198, COURSES.get(2)),
        new Woman("Luciana Gomes Rocha", LocalDate.of(1983, 5, 9), "(81) 99120-7745", 176, COURSES.get(3)),
        new Woman("Francisca Oliveira Santos", LocalDate.of(1969, 1, 30), "(81) 98401-6618", 160, COURSES.get(4)),
        new Woman("Beatriz Nascimento", LocalDate.of(1999, 9, 17), "(81) 99287-3051", 141, COURSES.get(0)),
        new Woman("Cláudia Moura Albuquerque", LocalDate.of(1980, 4, 5), "(81) 98576-9924", 122, COURSES.get(1)),
        new Woman("Débora Cavalcanti", LocalDate.of(1995, 12, 11), "(81) 99731-4486", 104, COURSES.get(2)),
        new Woman("Eliane Barbosa Pereira", LocalDate.of(1972, 6, 28), "(81) 98190-5573", 87, COURSES.get(3)),
        new Woman("Giovana Ribeiro", LocalDate.of(2001, 2, 19), "(81) 99402-8817", 70, COURSES.get(4)),
        new Woman("Helena Carvalho Melo", LocalDate.of(1986, 8, 3), "(81) 98265-1130", 52, COURSES.get(0)),
        new Woman("Ivone Monteiro", LocalDate.of(1965, 10, 21), "(81) 99815-6694", 36, COURSES.get(1)),
        new Woman("Josefa Araújo Lins", LocalDate.of(1978, 3, 8), "(81) 98647-2208", 18, COURSES.get(2)),
        new Woman("Larissa Fernandes", LocalDate.of(2003, 7, 15), "(81) 99058-3379", 6, COURSES.get(3))
    );

    final UserRepository userRepository;
    final CategoryRepository categoryRepository;
    final CategoryFieldRepository categoryFieldRepository;
    final PermissionRepository permissionRepository;
    final ClientRepository clientRepository;
    final DynamicEntityRepository dynamicEntityRepository;
    final ActivityLedgerRepository activityLedgerRepository;
    final FileRecordRepository fileRecordRepository;
    final CategoryActivityTypeRepository activityTypeRepository;
    final FileStorage fileStorage;
    final PasswordEncoder passwordEncoder;
    final String seedPassword;

    private final Random random = new Random(42);
    private final byte[] blankPdf = blankPdf();
    private int activityCount;
    private int fileCount;
    private final Map<String, CategoryActivityType> clientTypes = new HashMap<>();

    public DemoDataSeeder(UserRepository userRepository, CategoryRepository categoryRepository,
            CategoryFieldRepository categoryFieldRepository, PermissionRepository permissionRepository,
            ClientRepository clientRepository, DynamicEntityRepository dynamicEntityRepository,
            ActivityLedgerRepository activityLedgerRepository, FileRecordRepository fileRecordRepository,
            FileStorage fileStorage, PasswordEncoder passwordEncoder,
            CategoryActivityTypeRepository activityTypeRepository,
            @Value("${app.seed.password:Demo@2026}") String seedPassword) {
        this.activityTypeRepository = activityTypeRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.categoryFieldRepository = categoryFieldRepository;
        this.permissionRepository = permissionRepository;
        this.clientRepository = clientRepository;
        this.dynamicEntityRepository = dynamicEntityRepository;
        this.activityLedgerRepository = activityLedgerRepository;
        this.fileRecordRepository = fileRecordRepository;
        this.fileStorage = fileStorage;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.existsByName(CLIENTS_CATEGORY)) {
            log.info("Seed de demonstração já aplicado (categoria '{}' existe); nada a fazer.", CLIENTS_CATEGORY);
            return;
        }

        Staff staff = createStaff();
        Category clientsCategory = categoryRepository.save(Category.builder()
            .name(CLIENTS_CATEGORY).entityType("native").tableReference(RecordType.CLIENT.getTableName())
            .isActive(true).build());
        Category coursesCategory = createCoursesCategory();
        clientTypes.putAll(createActivityTypes(clientsCategory, CLIENT_ACTIVITY_TYPES));
        createActivityTypes(coursesCategory, COURSE_ACTIVITY_TYPES);
        createPermissions(staff, clientsCategory, coursesCategory);

        for (int i = 0; i < WOMEN.size(); i++) {
            seedWoman(WOMEN.get(i), JOURNEYS.get(i % JOURNEYS.size()), staff, coursesCategory);
        }

        log.info("Seed de demonstração aplicado: {} mulheres, {} atividades, {} documentos.",
            WOMEN.size(), activityCount, fileCount);
        log.info("Logins de demonstração (senha: {}):", seedPassword);
        List.of(staff.coordinator(), staff.psychologist(), staff.socialWorker(), staff.receptionist(), staff.instructor())
            .forEach(user -> log.info("  {} <{}>", user.getName(), user.getEmail()));
    }

    // ------------------------------------------------------------------ estrutura

    private Staff createStaff() {
        return new Staff(
            user("Juliana Andrade (Coordenação)", "coordenacao@secmulher.demo", true),
            user("Fernanda Lima (Psicóloga)", "psicologia@secmulher.demo", false),
            user("Patrícia Souza (Assistente Social)", "assistencia@secmulher.demo", false),
            user("Camila Rocha (Recepção)", "recepcao@secmulher.demo", false),
            user("Renata Alves (Instrutora de Cursos)", "cursos@secmulher.demo", false)
        );
    }

    private User user(String name, String email, boolean admin) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
            .name(name).email(email).passwordHash(passwordEncoder.encode(seedPassword))
            .isGlobalAdmin(admin).isActive(true).build()));
    }

    private Category createCoursesCategory() {
        Category category = categoryRepository.save(Category.builder()
            .name(COURSES_CATEGORY).entityType("dynamic").tableReference(RecordType.DYNAMIC_ENTITY.getTableName())
            .isActive(true).build());

        field(category, "participante", "Participante", FieldType.TEXT, true, null, 0);
        field(category, "cliente_id", "Código da atendida", FieldType.TEXT, false, null, 1);
        field(category, "curso", "Curso", FieldType.LIST, true, COURSES, 2);
        field(category, "turno", "Turno", FieldType.LIST, true, List.of("Manhã", "Tarde", "Noite"), 3);
        field(category, "situacao", "Situação", FieldType.LIST, true, List.of("Inscrita", "Cursando", "Concluída", "Desistente"), 4);
        field(category, "data_inicio", "Data de início", FieldType.DATE, false, null, 5);
        return category;
    }

    private void field(Category category, String key, String label, FieldType type, boolean required,
            List<String> options, int position) {
        categoryFieldRepository.save(CategoryField.builder()
            .category(category).key(key).label(label).fieldType(type)
            .isRequired(required).options(options).position(position).build());
    }

    // Cada perfil enxerga só o necessário: a recepção não vê situação de risco nem laudos,
    // a instrutora só vê contato das atendidas, e apenas a psicóloga acessa documentos sigilosos
    // e registra "Atendimento psicológico" (a assistente social pode registrar todos os outros tipos)
    private void createPermissions(Staff staff, Category clients, Category courses) {
        permission(staff.psychologist(), clients, true, true, true, false, Map.of(), ConfidentialityLevel.CONFIDENTIAL);
        Permission social = permission(staff.socialWorker(), clients, true, true, true, false, Map.of(),
            ConfidentialityLevel.RESTRICTED);
        social.setAllowedActivityTypes(CLIENT_ACTIVITY_TYPES.stream()
            .map(SeedType::key).filter(key -> !key.equals("atendimento_psicologico")).toList());
        permissionRepository.save(social);
        permission(staff.receptionist(), clients, true, true, false, false,
            Map.of("read", List.of("nome", "telefone", "data_nascimento"),
                "write", List.of("nome", "telefone", "data_nascimento")),
            ConfidentialityLevel.INTERNAL);
        permission(staff.instructor(), clients, true, false, false, false,
            Map.of("read", List.of("nome", "telefone"), "write", List.of()), ConfidentialityLevel.INTERNAL);

        permission(staff.socialWorker(), courses, true, true, true, false, Map.of(), ConfidentialityLevel.RESTRICTED);
        permission(staff.instructor(), courses, true, true, true, true, Map.of(), ConfidentialityLevel.INTERNAL);
        permission(staff.psychologist(), courses, true, false, false, false, Map.of(), ConfidentialityLevel.INTERNAL);
    }

    private Permission permission(User user, Category category, boolean read, boolean create, boolean update,
            boolean delete, Map<String, Object> columns, ConfidentialityLevel level) {
        return permissionRepository.save(Permission.builder()
            .user(user).category(category).canRead(read).canCreate(create).canUpdate(update).canDelete(delete)
            .allowedColumns(new LinkedHashMap<>(columns)).maxConfidentiality(level).build());
    }

    // ------------------------------------------------------------------ histórico

    private void seedWoman(Woman woman, List<Step> journey, Staff staff, Category coursesCategory) {
        LocalDate start = LocalDate.now().minusDays(woman.daysAgo());
        LocalDateTime arrival = start.atTime(8 + random.nextInt(4), random.nextInt(4) * 15);

        Client client = clientRepository.save(Client.builder()
            .nome(woman.nome()).dataNascimento(woman.nascimento()).telefone(woman.telefone())
            .sobRisco(false).createdBy(staff.receptionist()).build());
        ledger(RecordType.CLIENT, client.getId(), ActivityType.CREATED, "Cadastro criado na recepção", null, null,
            staff.receptionist(), arrival);

        DynamicEntity enrollment = null;
        int sameDayIndex = 0;
        for (Step step : journey) {
            LocalDateTime when = step.day() == 0
                ? arrival.plusMinutes(15L * ++sameDayIndex)
                : start.plusDays(step.day()).atTime(8 + random.nextInt(9), random.nextInt(4) * 15);
            if (when.isAfter(LocalDateTime.now())) {
                break; // casos recentes ficam em andamento
            }

            User author = staff.forRole(step.role());
            String responsible = step.role() == Role.LEGAL ? LAWYER : author.getName();
            String description = step.description().replace("{curso}", woman.curso());

            switch (step.effect()) {
                case MARK_RISK -> {
                    client.setSobRisco(true);
                    clientRepository.save(client);
                    Map<String, Object> changes = new LinkedHashMap<>();
                    ActivityService.trackChange(changes, "sob_risco", false, true);
                    ledger(RecordType.CLIENT, client.getId(), ActivityType.UPDATED, "Cadastro atualizado", changes,
                        null, author, when);
                }
                case ENROLL -> {
                    manual(client.getId(), step, description, responsible, author, when);
                    enrollment = enroll(client, woman, coursesCategory, author, when);
                }
                case COMPLETE_COURSE -> {
                    manual(client.getId(), step, description, responsible, author, when);
                    if (enrollment != null) {
                        completeCourse(enrollment, author, when);
                    }
                }
                case NONE -> manual(client.getId(), step, description, responsible, author, when);
            }

            if (step.fileName() != null) {
                attachPdf(client.getId(), step.fileName().replace("{curso}", woman.curso()), step.level(), author,
                    when.plusMinutes(5));
            }
        }
    }

    private DynamicEntity enroll(Client client, Woman woman, Category coursesCategory, User author, LocalDateTime when) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("participante", woman.nome());
        fields.put("cliente_id", client.getId().toString());
        fields.put("curso", woman.curso());
        fields.put("turno", List.of("Manhã", "Tarde", "Noite").get(random.nextInt(3)));
        fields.put("situacao", "Cursando");
        fields.put("data_inicio", when.toLocalDate().plusDays(2).toString());

        DynamicEntity enrollment = dynamicEntityRepository.save(DynamicEntity.builder()
            .category(coursesCategory).customFields(fields).createdBy(author).build());
        ledger(RecordType.DYNAMIC_ENTITY, enrollment.getId(), ActivityType.CREATED,
            "Cadastro criado em " + COURSES_CATEGORY, null, null, author, when);
        return enrollment;
    }

    private void completeCourse(DynamicEntity enrollment, User author, LocalDateTime when) {
        Map<String, Object> fields = new LinkedHashMap<>(enrollment.getCustomFields());
        fields.put("situacao", "Concluída");
        enrollment.setCustomFields(fields);
        dynamicEntityRepository.save(enrollment);

        Map<String, Object> changes = new LinkedHashMap<>();
        ActivityService.trackChange(changes, "situacao", "Cursando", "Concluída");
        ledger(RecordType.DYNAMIC_ENTITY, enrollment.getId(), ActivityType.UPDATED, "Cadastro atualizado", changes,
            null, author, when);
    }

    private void attachPdf(UUID clientId, String fileName, ConfidentialityLevel level, User author, LocalDateTime when) {
        String storagePath = RecordType.CLIENT.getTableName() + "/" + clientId + "/" + UUID.randomUUID();
        fileStorage.store(new ByteArrayInputStream(blankPdf), storagePath);

        fileRecordRepository.save(FileRecord.builder()
            .entityType(RecordType.CLIENT.getTableName()).entityId(clientId).uploadedBy(author)
            .originalName(fileName).storagePath(storagePath).fileSizeBytes((long) blankPdf.length)
            .contentType("application/pdf").confidentialityLevel(level).build());
        fileCount++;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("arquivo", fileName);
        details.put("sigilo", level.name());
        ledger(RecordType.CLIENT, clientId, ActivityType.FILE_UPLOADED, "Documento anexado: " + fileName, details,
            null, author, when);
    }

    private Map<String, CategoryActivityType> createActivityTypes(Category category, List<SeedType> types) {
        Map<String, CategoryActivityType> byKey = new HashMap<>();
        for (int i = 0; i < types.size(); i++) {
            SeedType seed = types.get(i);
            byKey.put(seed.key(), activityTypeRepository.save(CategoryActivityType.builder()
                .category(category).key(seed.key()).label(seed.label()).color(seed.color())
                .position(i).isActive(true).build()));
        }
        return byKey;
    }

    // Tipo configurável correspondente a cada passo das trajetórias
    private static String activityTypeKey(Step step) {
        return switch (step.type()) {
            case TRIAGE -> "triagem";
            case SERVICE -> switch (step.role()) {
                case PSYCHOLOGY -> "atendimento_psicologico";
                case LEGAL -> "atendimento_juridico";
                case INSTRUCTOR -> "atividade_curso";
                default -> "atendimento";
            };
            case FOLLOW_UP -> step.description().startsWith("Visita domiciliar") ? "visita_domiciliar" : "acompanhamento";
            default -> "anotacao";
        };
    }

    private void manual(UUID clientId, Step step, String description, String responsibleEmployee, User user,
            LocalDateTime occurredAt) {
        CategoryActivityType type = clientTypes.get(activityTypeKey(step));
        activityLedgerRepository.save(ActivityLedger.builder()
            .entityType(RecordType.CLIENT.getTableName()).entityId(clientId).user(user)
            .actionType(ActivityType.MANUAL).activityType(type).activityTypeLabel(type.getLabel())
            .description(description).responsibleEmployee(responsibleEmployee)
            .occurredAt(occurredAt).build());
        activityCount++;
    }

    private void ledger(RecordType type, UUID recordId, ActivityType actionType, String description,
            Map<String, Object> changes, String responsibleEmployee, User user, LocalDateTime occurredAt) {
        activityLedgerRepository.save(ActivityLedger.builder()
            .entityType(type.getTableName()).entityId(recordId).user(user).actionType(actionType)
            .description(description).changes(changes).responsibleEmployee(responsibleEmployee)
            .occurredAt(occurredAt).build());
        activityCount++;
    }

    // ------------------------------------------------------------------ PDF

    // PDF válido de uma página A4 em branco, com a tabela xref calculada byte a byte
    private static byte[] blankPdf() {
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>"
        };
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xrefOffset = pdf.length();
        pdf.append("xref\n0 ").append(objects.length + 1).append('\n').append("0000000000 65535 f \n");
        offsets.forEach(offset -> pdf.append(String.format("%010d 00000 n \n", offset)));
        pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\n")
            .append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
