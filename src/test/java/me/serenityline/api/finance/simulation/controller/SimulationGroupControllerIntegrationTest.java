package me.serenityline.api.finance.simulation.controller;

import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SimulationGroupControllerIntegrationTest extends IntegrationTestSupport {

    private static final String SIMULATION_GROUPS_PATH = "/api/finance/simulation-groups";
    private static final String USER_EMAIL_DOMAIN = "example.com";
    private static final String DEFAULT_PASSWORD_HASH = "test-password-hash";
    private static final String DEFAULT_LOCALE = "it-IT";
    private static final String DEFAULT_THEME = "DEFAULT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String createRequestWithoutAccountIdsJson(
            String simulationGroupName,
            String simulationGroupDescription
    ) {
        return """
                {
                  "simulationGroupName": %s,
                  "simulationGroupDescription": %s
                }
                """.formatted(
                jsonString(simulationGroupName),
                jsonNullableString(simulationGroupDescription)
        );
    }

    private static String createRequestWithEmptyAccountIdsJson(
            String simulationGroupName,
            String simulationGroupDescription
    ) {
        return """
                {
                  "simulationGroupName": %s,
                  "simulationGroupDescription": %s,
                  "accountIds": []
                }
                """.formatted(
                jsonString(simulationGroupName),
                jsonNullableString(simulationGroupDescription)
        );
    }

    private static String createRequestJson(
            String simulationGroupName,
            String simulationGroupDescription,
            UUID... accountIds
    ) {
        return """
                {
                  "simulationGroupName": %s,
                  "simulationGroupDescription": %s,
                  "accountIds": %s
                }
                """.formatted(
                jsonString(simulationGroupName),
                jsonNullableString(simulationGroupDescription),
                accountIdsJson(accountIds)
        );
    }

    private static String accountIdsJson(UUID... accountIds) {
        return Arrays.stream(accountIds)
                .map(UUID::toString)
                .map(SimulationGroupControllerIntegrationTest::jsonString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String jsonNullableString(String value) {
        if (value == null) {
            return "null";
        }

        return jsonString(value);
    }

    private static String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                + "\"";
    }

    @Test
    void createSimulationGroupShouldRequireAuthentication() throws Exception {
        String requestBody = createRequestJson(
                "Scenario cambio lavoro",
                "Simulazione riduzione stipendio",
                UUID.randomUUID()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldCreateSimulationGroupWithoutAccounts() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        String accessToken = accessTokenFor(owner);
        String requestBody = createRequestWithoutAccountIdsJson(
                "Scenario libero",
                "Bozza senza conti"
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationGroupId").isString())
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario libero"))
                .andExpect(jsonPath("$.simulationGroupDescription").value("Bozza senza conti"))
                .andExpect(jsonPath("$.simulationGroupCreatedAt").exists())
                .andExpect(jsonPath("$.simulationGroupUpdatedAt").exists())
                .andExpect(jsonPath("$.accountIds", hasSize(0)));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario libero"))
                .isEqualTo(1L);
    }

    @Test
    void superCollaboratorShouldCreateSimulationGroupWithoutAccounts() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");
        String accessToken = accessTokenFor(superCollaborator);
        String requestBody = createRequestWithoutAccountIdsJson(
                "Scenario super",
                null
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario super"))
                .andExpect(jsonPath("$.accountIds", hasSize(0)));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario super"))
                .isEqualTo(1L);
    }

    @Test
    void ownerShouldCreateSimulationGroupWithAnyGroupAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto principale");
        String accessToken = accessTokenFor(owner);
        String requestBody = createRequestJson(
                "Scenario mutuo",
                "Simulazione estinzione mutuo",
                account.accountId()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario mutuo"))
                .andExpect(jsonPath("$.accountIds", hasSize(1)))
                .andExpect(jsonPath("$.accountIds[0]").value(account.accountId().toString()));

        UUID simulationGroupId = findSimulationGroupId(owner.userGroupId(), "Scenario mutuo");

        assertThat(countSimulationGroupAccountLinks(
                simulationGroupId,
                account.accountId(),
                owner.userGroupId()
        )).isEqualTo(1L);
    }

    @Test
    void viewerCollaboratorShouldCreateSimulationGroupWithAccessibleAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");
        AccountRef account = createAccount(owner.userGroupId(), "Conto viewer");
        grantAccountAccess(account, viewer);

        String accessToken = accessTokenFor(viewer);
        String requestBody = createRequestJson(
                "Scenario viewer",
                "Scenario creato da viewer",
                account.accountId()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario viewer"))
                .andExpect(jsonPath("$.accountIds", hasSize(1)))
                .andExpect(jsonPath("$.accountIds[0]").value(account.accountId().toString()));

        UUID simulationGroupId = findSimulationGroupId(owner.userGroupId(), "Scenario viewer");

        assertThat(countSimulationGroupAccountLinks(
                simulationGroupId,
                account.accountId(),
                owner.userGroupId()
        )).isEqualTo(1L);
    }

    @Test
    void collaboratorShouldCreateSimulationGroupWithAccessibleAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");
        AccountRef account = createAccount(owner.userGroupId(), "Conto collaborator");
        grantAccountAccess(account, collaborator);

        String accessToken = accessTokenFor(collaborator);
        String requestBody = createRequestJson(
                "Scenario collaborator",
                "Scenario creato da collaborator",
                account.accountId()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario collaborator"))
                .andExpect(jsonPath("$.accountIds", hasSize(1)))
                .andExpect(jsonPath("$.accountIds[0]").value(account.accountId().toString()));

        UUID simulationGroupId = findSimulationGroupId(owner.userGroupId(), "Scenario collaborator");

        assertThat(countSimulationGroupAccountLinks(
                simulationGroupId,
                account.accountId(),
                owner.userGroupId()
        )).isEqualTo(1L);
    }

    @Test
    void viewerCollaboratorShouldNotCreateSimulationGroupWithoutAccounts() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");
        String accessToken = accessTokenFor(viewer);
        String requestBody = createRequestWithEmptyAccountIdsJson(
                "Scenario viewer senza conti",
                null
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.accountIdsRequired"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario viewer senza conti"))
                .isZero();
    }

    @Test
    void collaboratorShouldNotCreateSimulationGroupWithoutAccounts() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");
        String accessToken = accessTokenFor(collaborator);
        String requestBody = createRequestWithEmptyAccountIdsJson(
                "Scenario collaborator senza conti",
                null
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.accountIdsRequired"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario collaborator senza conti"))
                .isZero();
    }

    @Test
    void viewerCollaboratorShouldReceiveForbiddenWhenAccountExistsButIsNotOperable() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");
        AccountRef account = createAccount(owner.userGroupId(), "Conto non operativo viewer");

        String accessToken = accessTokenFor(viewer);
        String requestBody = createRequestJson(
                "Scenario viewer non operativo",
                null,
                account.accountId()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("finance.account.operationNotAllowed"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario viewer non operativo"))
                .isZero();
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenAccountExistsButIsNotAccessible() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");
        AccountRef account = createAccount(owner.userGroupId(), "Conto nascosto collaborator");

        String accessToken = accessTokenFor(collaborator);
        String requestBody = createRequestJson(
                "Scenario collaborator nascosto",
                null,
                account.accountId()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario collaborator nascosto"))
                .isZero();
    }

    @Test
    void ownerShouldRejectAccountFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");
        AccountRef otherGroupAccount = createAccount(otherOwner.userGroupId(), "Conto altro gruppo");

        String accessToken = accessTokenFor(owner);
        String requestBody = createRequestJson(
                "Scenario cross group",
                null,
                otherGroupAccount.accountId()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario cross group"))
                .isZero();
    }

    @Test
    void ownerShouldReceiveExplicitDuplicateNameError() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        createSimulationGroup(owner.userGroupId(), "Scenario duplicato");

        String accessToken = accessTokenFor(owner);
        String requestBody = createRequestWithoutAccountIdsJson(
                "Scenario duplicato",
                null
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.nameAlreadyExists"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario duplicato"))
                .isEqualTo(1L);
    }

    @Test
    void viewerCollaboratorShouldReceiveExplicitDuplicateNameError() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");
        AccountRef account = createAccount(owner.userGroupId(), "Conto viewer duplicato");
        grantAccountAccess(account, viewer);
        createSimulationGroup(owner.userGroupId(), "Scenario già esistente");

        String accessToken = accessTokenFor(viewer);
        String requestBody = createRequestJson(
                "Scenario già esistente",
                null,
                account.accountId()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.nameAlreadyExists"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario già esistente"))
                .isEqualTo(1L);
    }

    @Test
    void collaboratorShouldNotLearnDuplicateNameExistence() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");
        AccountRef account = createAccount(owner.userGroupId(), "Conto collaborator duplicato");
        grantAccountAccess(account, collaborator);
        createSimulationGroup(owner.userGroupId(), "Scenario segreto");

        String accessToken = accessTokenFor(collaborator);
        String requestBody = createRequestJson(
                "Scenario segreto",
                null,
                account.accountId()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.nameNotAllowed"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario segreto"))
                .isEqualTo(1L);
    }

    @Test
    void createSimulationGroupShouldRejectNormalizedDuplicateName() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        createSimulationGroup(owner.userGroupId(), "Cambio lavoro");

        String accessToken = accessTokenFor(owner);
        String requestBody = createRequestWithoutAccountIdsJson(
                "  CAMBIO   LAVORO  ",
                null
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.nameAlreadyExists"));

        assertThat(countActiveSimulationGroupsByNormalizedName(owner.userGroupId(), "cambio lavoro"))
                .isEqualTo(1L);
    }

    @Test
    void createSimulationGroupShouldAllowSameNameInDifferentGroups() throws Exception {
        UserRef firstOwner = createUserWithNewGroup("OWNER");
        UserRef secondOwner = createUserWithNewGroup("OWNER");

        createSimulationGroup(firstOwner.userGroupId(), "Scenario condiviso");

        String accessToken = accessTokenFor(secondOwner);
        String requestBody = createRequestWithoutAccountIdsJson(
                "Scenario condiviso",
                null
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario condiviso"));

        assertThat(countSimulationGroupsByName(firstOwner.userGroupId(), "Scenario condiviso"))
                .isEqualTo(1L);

        assertThat(countSimulationGroupsByName(secondOwner.userGroupId(), "Scenario condiviso"))
                .isEqualTo(1L);
    }

    @Test
    void createSimulationGroupShouldTrimNameAndDescription() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        String accessToken = accessTokenFor(owner);

        String requestBody = createRequestWithoutAccountIdsJson(
                "  Scenario pulito  ",
                "  Descrizione pulita  "
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario pulito"))
                .andExpect(jsonPath("$.simulationGroupDescription").value("Descrizione pulita"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario pulito"))
                .isEqualTo(1L);
    }

    @Test
    void createSimulationGroupShouldRejectBlankDescription() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        String accessToken = accessTokenFor(owner);

        String requestBody = createRequestWithoutAccountIdsJson(
                "Scenario descrizione blank",
                "   "
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.descriptionBlank"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario descrizione blank"))
                .isZero();
    }

    @Test
    void createSimulationGroupShouldAllowSameNameWhenExistingOneIsArchived() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        createArchivedSimulationGroup(owner.userGroupId(), "Scenario archiviato");

        String accessToken = accessTokenFor(owner);
        String requestBody = createRequestWithoutAccountIdsJson(
                "Scenario archiviato",
                null
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario archiviato"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario archiviato"))
                .isEqualTo(2L);
    }

    @Test
    void createSimulationGroupShouldIgnoreDuplicateAccountIds() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto duplicato request");

        String accessToken = accessTokenFor(owner);

        String requestBody = """
                {
                  "simulationGroupName": "Scenario account duplicati",
                  "simulationGroupDescription": null,
                  "accountIds": ["%s", "%s"]
                }
                """.formatted(account.accountId(), account.accountId());

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountIds", hasSize(1)))
                .andExpect(jsonPath("$.accountIds[0]").value(account.accountId().toString()));

        UUID simulationGroupId = findSimulationGroupId(
                owner.userGroupId(),
                "Scenario account duplicati"
        );

        assertThat(countSimulationGroupAccountLinks(
                simulationGroupId,
                account.accountId(),
                owner.userGroupId()
        )).isEqualTo(1L);
    }

    @Test
    void createSimulationGroupShouldNotPersistAnythingWhenOneAccountIsInvalid() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef validAccount = createAccount(owner.userGroupId(), "Conto valido");
        UUID invalidAccountId = UUID.randomUUID();

        String accessToken = accessTokenFor(owner);

        String requestBody = createRequestJson(
                "Scenario rollback",
                null,
                validAccount.accountId(),
                invalidAccountId
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario rollback"))
                .isZero();
    }

    @Test
    void viewerCollaboratorShouldReceiveNotFoundWhenAccountDoesNotExist() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        String accessToken = accessTokenFor(viewer);

        String requestBody = createRequestJson(
                "Scenario viewer account inesistente",
                null,
                UUID.randomUUID()
        );

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario viewer account inesistente"))
                .isZero();
    }

    @Test
    void createSimulationGroupShouldRejectNullAccountId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        String accessToken = accessTokenFor(owner);

        String requestBody = """
                {
                  "simulationGroupName": "Scenario account null",
                  "simulationGroupDescription": null,
                  "accountIds": [null]
                }
                """;

        mockMvc.perform(post(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.accountIdRequired"));

        assertThat(countSimulationGroupsByName(owner.userGroupId(), "Scenario account null"))
                .isZero();
    }

    @Test
    void findSimulationGroupsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(SIMULATION_GROUPS_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldSeeOnlyActiveSimulationGroupsByDefault() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        createSimulationGroup(owner.userGroupId(), "Scenario attivo");
        createArchivedSimulationGroup(owner.userGroupId(), "Scenario archiviato");
        createSimulationGroup(otherOwner.userGroupId(), "Scenario altro gruppo");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].simulationGroupName").value("Scenario attivo"))
                .andExpect(jsonPath("$[0].accountIds", hasSize(0)));
    }

    @Test
    void ownerShouldFilterArchivedSimulationGroups() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        createSimulationGroup(owner.userGroupId(), "Scenario attivo");
        createArchivedSimulationGroup(owner.userGroupId(), "Scenario archiviato");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .param("status", "ARCHIVED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].simulationGroupName").value("Scenario archiviato"))
                .andExpect(jsonPath("$[0].simulationGroupArchivedAt").exists());
    }

    @Test
    void ownerShouldFilterAllSimulationGroups() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        createSimulationGroup(owner.userGroupId(), "A scenario attivo");
        createArchivedSimulationGroup(owner.userGroupId(), "B scenario archiviato");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .param("status", "ALL")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].simulationGroupName", contains(
                        "A scenario attivo",
                        "B scenario archiviato"
                )));
    }

    @Test
    void findSimulationGroupsShouldRejectInvalidStatus() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .param("status", "DELETED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.status.invalid"));
    }

    @Test
    void viewerCollaboratorShouldSeeAllSimulationGroupsIncludingUnlinkedAndNonOperableAccounts() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef nonOperableAccount = createAccount(owner.userGroupId(), "Conto visibile ma non operativo viewer");

        UUID unlinkedSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "A scenario senza conti"
        );

        UUID linkedSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "B scenario conto non operativo"
        );

        linkSimulationGroupToAccount(linkedSimulationGroupId, nonOperableAccount);

        String accessToken = accessTokenFor(viewer);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].simulationGroupName", contains(
                        "A scenario senza conti",
                        "B scenario conto non operativo"
                )))
                .andExpect(content().string(containsString(unlinkedSimulationGroupId.toString())))
                .andExpect(content().string(containsString(linkedSimulationGroupId.toString())))
                .andExpect(content().string(containsString(nonOperableAccount.accountId().toString())));
    }

    @Test
    void collaboratorShouldSeeOnlySimulationGroupsLinkedToAccessibleAccounts() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef accessibleAccount = createAccount(owner.userGroupId(), "Conto accessibile collaborator");
        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto nascosto collaborator");

        grantAccountAccess(accessibleAccount, collaborator);

        UUID visibleSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "A scenario visibile"
        );
        linkSimulationGroupToAccount(visibleSimulationGroupId, accessibleAccount);
        linkSimulationGroupToAccount(visibleSimulationGroupId, hiddenAccount);

        UUID hiddenSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "B scenario nascosto"
        );
        linkSimulationGroupToAccount(hiddenSimulationGroupId, hiddenAccount);

        createSimulationGroup(owner.userGroupId(), "C scenario senza conti");

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].simulationGroupName").value("A scenario visibile"))
                .andExpect(jsonPath("$[0].simulationGroupId").value(visibleSimulationGroupId.toString()))
                .andExpect(jsonPath("$[0].accountIds", hasSize(1)))
                .andExpect(jsonPath("$[0].accountIds[0]").value(accessibleAccount.accountId().toString()))
                .andExpect(content().string(not(containsString(hiddenAccount.accountId().toString()))))
                .andExpect(content().string(not(containsString(hiddenSimulationGroupId.toString()))))
                .andExpect(content().string(not(containsString("B scenario nascosto"))))
                .andExpect(content().string(not(containsString("C scenario senza conti"))));
    }

    @Test
    void collaboratorShouldSeeAccessibleArchivedSimulationGroupsWhenStatusIsArchived() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef accessibleAccount = createAccount(owner.userGroupId(), "Conto archived collaborator");
        grantAccountAccess(accessibleAccount, collaborator);

        UUID activeSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario attivo collaborator"
        );
        linkSimulationGroupToAccount(activeSimulationGroupId, accessibleAccount);

        UUID archivedSimulationGroupId = createArchivedSimulationGroup(
                owner.userGroupId(),
                "Scenario archiviato collaborator"
        );
        linkSimulationGroupToAccount(archivedSimulationGroupId, accessibleAccount);

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .param("status", "ARCHIVED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].simulationGroupName").value("Scenario archiviato collaborator"))
                .andExpect(jsonPath("$[0].simulationGroupId").value(archivedSimulationGroupId.toString()))
                .andExpect(jsonPath("$[0].simulationGroupArchivedAt").exists())
                .andExpect(jsonPath("$[0].accountIds", hasSize(1)))
                .andExpect(jsonPath("$[0].accountIds[0]").value(accessibleAccount.accountId().toString()));
    }

    @Test
    void superCollaboratorShouldSeeAllSimulationGroupsIncludingUnlinkedOnes() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        createSimulationGroup(owner.userGroupId(), "A scenario senza conti");
        createArchivedSimulationGroup(owner.userGroupId(), "B scenario archiviato");

        String accessToken = accessTokenFor(superCollaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .param("status", "ALL")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].simulationGroupName", contains(
                        "A scenario senza conti",
                        "B scenario archiviato"
                )));
    }

    @Test
    void collaboratorShouldFilterAllVisibleSimulationGroups() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef accessibleAccount = createAccount(owner.userGroupId(), "Conto accessibile all");
        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto nascosto all");

        grantAccountAccess(accessibleAccount, collaborator);

        UUID activeVisibleSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "A scenario attivo visibile"
        );
        linkSimulationGroupToAccount(activeVisibleSimulationGroupId, accessibleAccount);

        UUID archivedVisibleSimulationGroupId = createArchivedSimulationGroup(
                owner.userGroupId(),
                "B scenario archiviato visibile"
        );
        linkSimulationGroupToAccount(archivedVisibleSimulationGroupId, accessibleAccount);

        UUID hiddenSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "C scenario nascosto"
        );
        linkSimulationGroupToAccount(hiddenSimulationGroupId, hiddenAccount);

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .param("status", "ALL")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].simulationGroupName", contains(
                        "A scenario attivo visibile",
                        "B scenario archiviato visibile"
                )))
                .andExpect(content().string(not(containsString("C scenario nascosto"))))
                .andExpect(content().string(not(containsString(hiddenSimulationGroupId.toString()))));
    }

    @Test
    void findSimulationGroupsShouldAcceptTrimmedCaseInsensitiveStatus() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        createSimulationGroup(owner.userGroupId(), "A scenario attivo");
        createArchivedSimulationGroup(owner.userGroupId(), "B scenario archiviato");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .param("status", " all ")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].simulationGroupName", contains(
                        "A scenario attivo",
                        "B scenario archiviato"
                )));
    }

    @Test
    void collaboratorShouldReceiveEmptyListWhenNoSimulationGroupIsAccessible() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto nascosto empty");

        UUID hiddenSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario nascosto empty"
        );
        linkSimulationGroupToAccount(hiddenSimulationGroupId, hiddenAccount);

        createSimulationGroup(owner.userGroupId(), "Scenario senza conti empty");

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)))
                .andExpect(content().string(not(containsString("Scenario nascosto empty"))))
                .andExpect(content().string(not(containsString(hiddenSimulationGroupId.toString()))));
    }

    @Test
    void findSimulationGroupsShouldReturnResultsOrderedByNameCaseInsensitive() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        createSimulationGroup(owner.userGroupId(), "beta scenario");
        createSimulationGroup(owner.userGroupId(), "Alpha scenario");
        createSimulationGroup(owner.userGroupId(), "gamma scenario");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].simulationGroupName", contains(
                        "Alpha scenario",
                        "beta scenario",
                        "gamma scenario"
                )));
    }

    @Test
    void findSimulationGroupShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldFindSimulationGroupById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto detail owner");

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario detail owner"
        );
        linkSimulationGroupToAccount(simulationGroupId, account);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario detail owner"))
                .andExpect(jsonPath("$.simulationGroupDescription").value("Existing simulation group"))
                .andExpect(jsonPath("$.simulationGroupCreatedAt").exists())
                .andExpect(jsonPath("$.simulationGroupUpdatedAt").exists())
                .andExpect(jsonPath("$.accountIds", hasSize(1)))
                .andExpect(jsonPath("$.accountIds[0]").value(account.accountId().toString()));
    }

    @Test
    void ownerShouldReceiveNotFoundForSimulationGroupFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        UUID otherGroupSimulationGroupId = createSimulationGroup(
                otherOwner.userGroupId(),
                "Scenario altro gruppo detail"
        );

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + otherGroupSimulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void viewerCollaboratorShouldFindUnlinkedSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario detail viewer senza conti"
        );

        String accessToken = accessTokenFor(viewer);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario detail viewer senza conti"))
                .andExpect(jsonPath("$.accountIds", hasSize(0)));
    }

    @Test
    void viewerCollaboratorShouldSeeAccountIdsEvenWhenAccountIsNotOperable() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef nonOperableAccount = createAccount(
                owner.userGroupId(),
                "Conto detail viewer non operativo"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario detail viewer non operativo"
        );
        linkSimulationGroupToAccount(simulationGroupId, nonOperableAccount);

        String accessToken = accessTokenFor(viewer);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario detail viewer non operativo"))
                .andExpect(jsonPath("$.accountIds", hasSize(1)))
                .andExpect(jsonPath("$.accountIds[0]").value(nonOperableAccount.accountId().toString()));
    }

    @Test
    void collaboratorShouldFindSimulationGroupLinkedToAccessibleAccountAndHideOtherAccountIds() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef accessibleAccount = createAccount(
                owner.userGroupId(),
                "Conto detail collaborator accessibile"
        );
        AccountRef hiddenAccount = createAccount(
                owner.userGroupId(),
                "Conto detail collaborator nascosto"
        );

        grantAccountAccess(accessibleAccount, collaborator);

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario detail collaborator visibile"
        );
        linkSimulationGroupToAccount(simulationGroupId, accessibleAccount);
        linkSimulationGroupToAccount(simulationGroupId, hiddenAccount);

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario detail collaborator visibile"))
                .andExpect(jsonPath("$.accountIds", hasSize(1)))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.accountId().toString()))
                .andExpect(content().string(not(containsString(hiddenAccount.accountId().toString()))));
    }

    @Test
    void collaboratorShouldReceiveNotFoundForSimulationGroupLinkedOnlyToHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(
                owner.userGroupId(),
                "Conto detail collaborator hidden only"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario detail collaborator nascosto"
        );
        linkSimulationGroupToAccount(simulationGroupId, hiddenAccount);

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void collaboratorShouldReceiveNotFoundForUnlinkedSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario detail collaborator senza conti"
        );

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void collaboratorShouldFindArchivedSimulationGroupWhenLinkedToAccessibleAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef accessibleAccount = createAccount(
                owner.userGroupId(),
                "Conto detail archived collaborator"
        );
        grantAccountAccess(accessibleAccount, collaborator);

        UUID simulationGroupId = createArchivedSimulationGroup(
                owner.userGroupId(),
                "Scenario detail archived collaborator"
        );
        linkSimulationGroupToAccount(simulationGroupId, accessibleAccount);

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario detail archived collaborator"))
                .andExpect(jsonPath("$.simulationGroupArchivedAt").exists())
                .andExpect(jsonPath("$.accountIds", hasSize(1)))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.accountId().toString()));
    }

    @Test
    void findSimulationGroupShouldReturnNotFoundWhenSimulationGroupDoesNotExist() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void superCollaboratorShouldFindSimulationGroupById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto detail super"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario detail super"
        );
        linkSimulationGroupToAccount(simulationGroupId, account);

        String accessToken = accessTokenFor(superCollaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario detail super"))
                .andExpect(jsonPath("$.accountIds", hasSize(1)))
                .andExpect(jsonPath("$.accountIds[0]").value(account.accountId().toString()));
    }

    @Test
    void viewerCollaboratorShouldFindArchivedSimulationGroupById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        UUID simulationGroupId = createArchivedSimulationGroup(
                owner.userGroupId(),
                "Scenario detail archived viewer"
        );

        String accessToken = accessTokenFor(viewer);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.simulationGroupName").value("Scenario detail archived viewer"))
                .andExpect(jsonPath("$.simulationGroupArchivedAt").exists())
                .andExpect(jsonPath("$.accountIds", hasSize(0)));
    }

    @Test
    void collaboratorShouldNotReceiveHiddenSimulationGroupDataInNotFoundResponse() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(
                owner.userGroupId(),
                "Conto detail leak hidden"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Scenario detail leak hidden"
        );
        linkSimulationGroupToAccount(simulationGroupId, hiddenAccount);

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/" + simulationGroupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"))
                .andExpect(content().string(not(containsString("Scenario detail leak hidden"))))
                .andExpect(content().string(not(containsString(hiddenAccount.accountId().toString()))));
    }

    @Test
    void findSimulationGroupShouldRejectMalformedSimulationGroupId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(SIMULATION_GROUPS_PATH + "/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isBadRequest());
    }

    private UserRef createUserWithNewGroup(String role) {
        UUID userGroupId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO user_groups (
                            user_group_id,
                            user_group_name
                        )
                        VALUES (?, ?)
                        """,
                userGroupId,
                "Simulation group test " + UUID.randomUUID()
        );

        return createUser(userGroupId, role);
    }

    private UserRef createUser(UUID userGroupId, String role) {
        UUID userId = UUID.randomUUID();
        String email = "simulation-group-" + UUID.randomUUID() + "@" + USER_EMAIL_DOMAIN;

        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id,
                            user_name,
                            email,
                            user_group_id,
                            user_role,
                            user_platform_role,
                            preferred_locale,
                            preferred_theme,
                            wants_invoice,
                            payment_email_reminders_enabled,
                            user_password_hash,
                            user_is_enabled,
                            token_version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                "Simulation User",
                email,
                userGroupId,
                role,
                "USER",
                DEFAULT_LOCALE,
                DEFAULT_THEME,
                false,
                true,
                DEFAULT_PASSWORD_HASH,
                true,
                0L
        );

        return new UserRef(userId, userGroupId);
    }

    private AccountRef createAccount(UUID userGroupId, String accountName) {
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO accounts (
                            account_id,
                            account_name,
                            currency,
                            opening_balance_date,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                accountId,
                accountName + " " + UUID.randomUUID(),
                "EUR",
                LocalDate.of(2026, 1, 1),
                userGroupId
        );

        return new AccountRef(accountId, userGroupId);
    }

    private void grantAccountAccess(AccountRef account, UserRef user) {
        jdbcTemplate.update("""
                        INSERT INTO accounts_users (
                            account_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                account.accountId(),
                user.userId(),
                user.userGroupId()
        );
    }

    private UUID createSimulationGroup(UUID userGroupId, String simulationGroupName) {
        UUID simulationGroupId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO simulation_groups (
                            simulation_group_id,
                            user_group_id,
                            simulation_group_name,
                            simulation_group_description
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                simulationGroupId,
                userGroupId,
                simulationGroupName,
                "Existing simulation group"
        );

        return simulationGroupId;
    }

    private String accessTokenFor(UserRef userRef) {
        User user = userRepository.findById(userRef.userId())
                .orElseThrow();

        return jwtTokenService.createAccessToken(user)
                .token();
    }

    private UUID findSimulationGroupId(UUID userGroupId, String simulationGroupName) {
        return jdbcTemplate.queryForObject("""
                        SELECT simulation_group_id
                        FROM simulation_groups
                        WHERE user_group_id = ?
                          AND simulation_group_name = ?
                        """,
                UUID.class,
                userGroupId,
                simulationGroupName
        );
    }

    private long countSimulationGroupsByName(UUID userGroupId, String simulationGroupName) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM simulation_groups
                        WHERE user_group_id = ?
                          AND simulation_group_name = ?
                        """,
                Long.class,
                userGroupId,
                simulationGroupName
        );

        return count == null ? 0L : count;
    }

    private long countActiveSimulationGroupsByNormalizedName(
            UUID userGroupId,
            String normalizedName
    ) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM simulation_groups
                        WHERE user_group_id = ?
                          AND simulation_group_archived_at IS NULL
                          AND lower(btrim(regexp_replace(simulation_group_name, '[[:space:]]+', ' ', 'g'))) = ?
                        """,
                Long.class,
                userGroupId,
                normalizedName
        );

        return count == null ? 0L : count;
    }

    private long countSimulationGroupAccountLinks(
            UUID simulationGroupId,
            UUID accountId,
            UUID userGroupId
    ) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM simulation_groups_accounts
                        WHERE simulation_group_id = ?
                          AND account_id = ?
                          AND user_group_id = ?
                        """,
                Long.class,
                simulationGroupId,
                accountId,
                userGroupId
        );

        return count == null ? 0L : count;
    }

    private UUID createArchivedSimulationGroup(UUID userGroupId, String simulationGroupName) {
        UUID simulationGroupId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO simulation_groups (
                            simulation_group_id,
                            user_group_id,
                            simulation_group_name,
                            simulation_group_description,
                            simulation_group_archived_at
                        )
                        VALUES (?, ?, ?, ?, now())
                        """,
                simulationGroupId,
                userGroupId,
                simulationGroupName,
                "Archived simulation group"
        );

        return simulationGroupId;
    }

    private void linkSimulationGroupToAccount(
            UUID simulationGroupId,
            AccountRef account
    ) {
        jdbcTemplate.update("""
                        INSERT INTO simulation_groups_accounts (
                            simulation_group_id,
                            account_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                simulationGroupId,
                account.accountId(),
                account.userGroupId()
        );
    }

    private record UserRef(
            UUID userId,
            UUID userGroupId
    ) {
    }

    private record AccountRef(
            UUID accountId,
            UUID userGroupId
    ) {
    }

}