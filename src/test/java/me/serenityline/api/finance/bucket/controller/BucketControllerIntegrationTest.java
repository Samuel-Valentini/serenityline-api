package me.serenityline.api.finance.bucket.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.bucket.entity.BucketAccount;
import me.serenityline.api.finance.bucket.repository.BucketAccountRepository;
import me.serenityline.api.finance.bucket.repository.BucketRepository;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BucketControllerIntegrationTest extends IntegrationTestSupport {

    private static final String BUCKETS_PATH = "/api/finance/buckets";
    private static final String IT_LOCALE = "it-IT";
    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private BucketAccountRepository bucketAccountRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @Test
    void createBucketShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post(BUCKETS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio vacanze"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldCreateBucketWithoutLinkedAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Vestito elegante",
                                  "bucketDescription": "Risparmio per acquistare un vestito elegante"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucketName").value("Vestito elegante"))
                .andExpect(jsonPath("$.bucketDescription").value("Risparmio per acquistare un vestito elegante"))
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty())
                .andExpect(jsonPath("$.userGroupId").value(owner.getUserGroup().getUserGroupId().toString()))
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        Bucket bucket = bucketRepository.findById(bucketId).orElseThrow();

        assertThat(bucket.getBucketName()).isEqualTo("Vestito elegante");
        assertThat(bucket.getBucketDescription()).isEqualTo("Risparmio per acquistare un vestito elegante");
        assertThat(bucket.getBucketClosedAt()).isNull();
        assertThat(bucketAccountRepository.countByBucket_BucketId(bucketId)).isZero();
    }

    @Test
    void superCollaboratorShouldCreateBucketWithoutLinkedAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User superCollaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.SUPER_COLLABORATOR
        );

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Fondo tasse"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucketName").value("Fondo tasse"))
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty())
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        assertThat(bucketRepository.findById(bucketId)).isPresent();
        assertThat(bucketAccountRepository.countByBucket_BucketId(bucketId)).isZero();
    }

    @Test
    void viewerCollaboratorShouldCreateBucketWithoutLinkedAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio generico"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucketName").value("Portafoglio generico"))
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty())
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        assertThat(bucketRepository.findById(bucketId)).isPresent();
        assertThat(bucketAccountRepository.countByBucket_BucketId(bucketId)).isZero();
    }

    @Test
    void collaboratorShouldNotCreateBucketWithoutLinkedAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio collaborator"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.bucket.accountRequiredForCollaborator"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void collaboratorShouldCreateBucketWithAccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        grantAccountAccess(account, collaborator);

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Vestito elegante",
                                  "accountIds": ["%s"]
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucketName").value("Vestito elegante"))
                .andExpect(jsonPath("$.accountIds[0]").value(account.getAccountId().toString()))
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        assertThat(bucketRepository.findById(bucketId)).isPresent();
        assertThat(bucketAccountRepository.countByBucket_BucketId(bucketId)).isEqualTo(1);
    }

    @Test
    void viewerCollaboratorShouldCreateBucketWithAccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        grantAccountAccess(account, viewer);

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio casa",
                                  "accountIds": ["%s"]
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucketName").value("Portafoglio casa"))
                .andExpect(jsonPath("$.accountIds[0]").value(account.getAccountId().toString()))
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        assertThat(bucketRepository.findById(bucketId)).isPresent();
        assertThat(bucketAccountRepository.countByBucket_BucketId(bucketId)).isEqualTo(1);
    }

    @Test
    void collaboratorShouldReturnNotFoundWhenAccountIsNotAccessible() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account account = createAccount(owner.getUserGroup(), "Conto non assegnato");

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio non accessibile",
                                  "accountIds": ["%s"]
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void viewerCollaboratorShouldReturnNotFoundWhenAccountIsNotOperable() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Account account = createAccount(owner.getUserGroup(), "Conto non operativo");

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio viewer",
                                  "accountIds": ["%s"]
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void ownerShouldCreateBucketWithAnyGroupAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto gruppo");

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio vacanze",
                                  "accountIds": ["%s"]
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountIds[0]").value(account.getAccountId().toString()))
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        assertThat(bucketRepository.findById(bucketId)).isPresent();
        assertThat(bucketAccountRepository.countByBucket_BucketId(bucketId)).isEqualTo(1);
    }

    @Test
    void superCollaboratorShouldCreateBucketWithAnyGroupAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User superCollaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.SUPER_COLLABORATOR
        );

        Account account = createAccount(owner.getUserGroup(), "Conto gruppo");

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio emergenze",
                                  "accountIds": ["%s"]
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountIds[0]").value(account.getAccountId().toString()))
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        assertThat(bucketRepository.findById(bucketId)).isPresent();
        assertThat(bucketAccountRepository.countByBucket_BucketId(bucketId)).isEqualTo(1);
    }

    @Test
    void createBucketShouldReturnNotFoundWhenAccountBelongsToAnotherUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Account otherGroupAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio vietato",
                                  "accountIds": ["%s"]
                                }
                                """.formatted(otherGroupAccount.getAccountId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void createBucketShouldRejectDuplicateNormalizedNameForOwner() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        createBucket(owner.getUserGroup(), "Fondo vacanze");

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "  fondo    VACANZE  "
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.nameAlreadyExists"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isEqualTo(1);
    }

    @Test
    void createBucketShouldHideDuplicateNameExistenceFromCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account collaboratorAccount = createAccount(owner.getUserGroup(), "Conto collaborator");

        grantAccountAccess(collaboratorAccount, collaborator);

        createBucket(owner.getUserGroup(), "Vacanza segreta");

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "  vacanza    SEGRETA  ",
                                  "accountIds": ["%s"]
                                }
                                """.formatted(collaboratorAccount.getAccountId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.bucket.nameNotAllowed"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isEqualTo(1);
    }

    @Test
    void createBucketShouldValidateAccountAccessBeforeDuplicateNameForCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account notAccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        createBucket(owner.getUserGroup(), "Nome sensibile");

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Nome sensibile",
                                  "accountIds": ["%s"]
                                }
                                """.formatted(notAccessibleAccount.getAccountId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));
    }

    @Test
    void createBucketShouldAllowSameNormalizedNameInDifferentUserGroups() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        createBucket(owner.getUserGroup(), "Fondo vacanze");

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(otherOwner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "  fondo    VACANZE  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucketName").value("fondo    VACANZE"));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isEqualTo(1);
        assertThat(bucketRepository.countByUserGroup_UserGroupId(otherOwner.getUserGroup().getUserGroupId()))
                .isEqualTo(1);
    }

    @Test
    void createBucketShouldTrimNameAndDescriptionButPreserveInternalWhitespaceAndCase() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "  Fondo    VACANZE  ",
                                  "bucketDescription": "  Descrizione    con spazi  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucketName").value("Fondo    VACANZE"))
                .andExpect(jsonPath("$.bucketDescription").value("Descrizione    con spazi"))
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        Bucket bucket = bucketRepository.findById(bucketId).orElseThrow();

        assertThat(bucket.getBucketName()).isEqualTo("Fondo    VACANZE");
        assertThat(bucket.getBucketDescription()).isEqualTo("Descrizione    con spazi");
    }

    @Test
    void createBucketShouldStoreBlankDescriptionAsNull() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio senza descrizione",
                                  "bucketDescription": "     "
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        Bucket bucket = bucketRepository.findById(bucketId).orElseThrow();

        assertThat(bucket.getBucketDescription()).isNull();
    }

    @Test
    void createBucketShouldIgnoreDuplicateAccountIds() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        grantAccountAccess(account, collaborator);

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio duplicati",
                                  "accountIds": ["%s", "%s"]
                                }
                                """.formatted(account.getAccountId(), account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountIds.length()").value(1))
                .andExpect(jsonPath("$.accountIds[0]").value(account.getAccountId().toString()))
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        assertThat(bucketAccountRepository.countByBucket_BucketId(bucketId)).isEqualTo(1);
    }

    @Test
    void createBucketShouldRejectNullAccountId() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio account nullo",
                                  "accountIds": [null]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.bucket.accountId.required"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void createBucketShouldRejectBlankName() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "    "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.bucket.name.required"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void createBucketShouldRejectTooLongName() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        String tooLongName = "a".repeat(256);

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "%s"
                                }
                                """.formatted(tooLongName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("finance.bucket.name.tooLong"));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void createBucketShouldRejectTooLongDescription() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        String tooLongDescription = "a".repeat(2001);

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio descrizione lunga",
                                  "bucketDescription": "%s"
                                }
                                """.formatted(tooLongDescription)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("finance.bucket.description.tooLong"));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void bucketAccountShouldRejectDifferentUserGroups() {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio gruppo uno");
        Account otherGroupAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");

        assertThatThrownBy(() -> BucketAccount.link(
                bucket,
                otherGroupAccount,
                owner.getUserGroup()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.bucket.userGroupMismatch");
    }

    @Test
    void databaseShouldRejectDuplicateNormalizedActiveBucketName() {
        User owner = createVerifiedUser(UserRole.OWNER);

        insertBucket(owner.getUserGroup(), "Fondo vacanze");

        assertThatThrownBy(() -> insertBucket(owner.getUserGroup(), "  fondo    VACANZE  "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void collaboratorShouldNotCreateBucketWithEmptyAccountIds() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio collaborator",
                                  "accountIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.bucket.accountRequiredForCollaborator"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void createBucketShouldRejectMissingName() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketDescription": "Descrizione senza nome"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.bucket.name.required"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void createBucketShouldAllowSameNormalizedNameAsClosedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket closedBucket = createBucket(owner.getUserGroup(), "Fondo vacanze");

        jdbcTemplate.update(
                """
                        update buckets
                        set bucket_closed_at = now()
                        where bucket_id = ?
                        """,
                closedBucket.getBucketId()
        );

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "  fondo    VACANZE  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucketName").value("fondo    VACANZE"));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isEqualTo(2);
    }

    @Test
    void ownerShouldCreateBucketWithMultipleDistinctAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account firstAccount = createAccount(owner.getUserGroup(), "Conto principale");
        Account secondAccount = createAccount(owner.getUserGroup(), "Conto secondario");

        MvcResult result = mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio multi conto",
                                  "accountIds": ["%s", "%s"]
                                }
                                """.formatted(firstAccount.getAccountId(), secondAccount.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountIds.length()").value(2))
                .andReturn();

        UUID bucketId = bucketIdFrom(result);

        assertThat(bucketAccountRepository.countByBucket_BucketId(bucketId)).isEqualTo(2);
    }

    @Test
    void collaboratorShouldNotCreateBucketWhenAnyLinkedAccountIsNotAccessible() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account notAccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio parziale",
                                  "accountIds": ["%s", "%s"]
                                }
                                """.formatted(accessibleAccount.getAccountId(), notAccessibleAccount.getAccountId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isZero();
    }

    @Test
    void viewerCollaboratorShouldReceiveExplicitDuplicateNameError() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        createBucket(owner.getUserGroup(), "Fondo vacanze");

        mockMvc.perform(post(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "  fondo    VACANZE  "
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.nameAlreadyExists"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));

        assertThat(bucketRepository.countByUserGroup_UserGroupId(owner.getUserGroup().getUserGroupId()))
                .isEqualTo(1);
    }

    @Test
    void findBucketsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(BUCKETS_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findBucketShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(BUCKETS_PATH + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldFindAllActiveBucketsIncludingUnlinkedOnes() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        Bucket unlinkedBucket = createBucket(owner.getUserGroup(), "A portafoglio scollegato");
        Bucket linkedBucket = createBucket(owner.getUserGroup(), "B portafoglio collegato");
        Bucket closedBucket = createBucket(owner.getUserGroup(), "C portafoglio chiuso");

        linkBucketToAccount(linkedBucket, account);
        closeBucket(closedBucket);

        mockMvc.perform(get(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bucketId").value(unlinkedBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketName").value("A portafoglio scollegato"))
                .andExpect(jsonPath("$[0].accountIds").isArray())
                .andExpect(jsonPath("$[0].accountIds").isEmpty())
                .andExpect(jsonPath("$[1].bucketId").value(linkedBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[1].bucketName").value("B portafoglio collegato"))
                .andExpect(jsonPath("$[1].accountIds.length()").value(1))
                .andExpect(jsonPath("$[1].accountIds[0]").value(account.getAccountId().toString()));
    }

    @Test
    void superCollaboratorShouldFindAllActiveBucketsIncludingUnlinkedOnes() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User superCollaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.SUPER_COLLABORATOR
        );

        Bucket firstBucket = createBucket(owner.getUserGroup(), "A portafoglio scollegato");
        Bucket secondBucket = createBucket(owner.getUserGroup(), "B portafoglio visibile");

        mockMvc.perform(get(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bucketId").value(firstBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[1].bucketId").value(secondBucket.getBucketId().toString()));
    }

    @Test
    void viewerCollaboratorShouldFindAllActiveBucketsIncludingUnlinkedOnes() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        Bucket unlinkedBucket = createBucket(owner.getUserGroup(), "A portafoglio scollegato");
        Bucket linkedBucket = createBucket(owner.getUserGroup(), "B portafoglio collegato");

        linkBucketToAccount(linkedBucket, account);

        mockMvc.perform(get(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bucketId").value(unlinkedBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].accountIds").isEmpty())
                .andExpect(jsonPath("$[1].bucketId").value(linkedBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[1].accountIds.length()").value(1))
                .andExpect(jsonPath("$[1].accountIds[0]").value(account.getAccountId().toString()));
    }

    @Test
    void collaboratorShouldFindOnlyBucketsLinkedToAccessibleAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket unlinkedBucket = createBucket(owner.getUserGroup(), "A portafoglio scollegato");
        Bucket hiddenBucket = createBucket(owner.getUserGroup(), "B portafoglio nascosto");
        Bucket mixedBucket = createBucket(owner.getUserGroup(), "C portafoglio misto");
        Bucket visibleBucket = createBucket(owner.getUserGroup(), "D portafoglio visibile");

        linkBucketToAccount(hiddenBucket, inaccessibleAccount);
        linkBucketToAccount(mixedBucket, accessibleAccount);
        linkBucketToAccount(mixedBucket, inaccessibleAccount);
        linkBucketToAccount(visibleBucket, accessibleAccount);

        mockMvc.perform(get(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bucketId").value(mixedBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketName").value("C portafoglio misto"))
                .andExpect(jsonPath("$[0].accountIds.length()").value(1))
                .andExpect(jsonPath("$[0].accountIds[0]").value(accessibleAccount.getAccountId().toString()))
                .andExpect(jsonPath("$[1].bucketId").value(visibleBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[1].bucketName").value("D portafoglio visibile"))
                .andExpect(jsonPath("$[1].accountIds.length()").value(1))
                .andExpect(jsonPath("$[1].accountIds[0]").value(accessibleAccount.getAccountId().toString()));

        assertThat(unlinkedBucket.getBucketId()).isNotEqualTo(mixedBucket.getBucketId());
        assertThat(hiddenBucket.getBucketId()).isNotEqualTo(visibleBucket.getBucketId());
    }

    @Test
    void ownerShouldFindBucketDetailForUnlinkedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");

        mockMvc.perform(get(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketName").value("Portafoglio scollegato"))
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty())
                .andExpect(jsonPath("$.userGroupId").value(owner.getUserGroup().getUserGroupId().toString()));
    }

    @Test
    void collaboratorShouldFindBucketDetailWhenLinkedToAccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio misto");

        linkBucketToAccount(bucket, accessibleAccount);
        linkBucketToAccount(bucket, inaccessibleAccount);

        mockMvc.perform(get(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketName").value("Portafoglio misto"))
                .andExpect(jsonPath("$.accountIds.length()").value(1))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.getAccountId().toString()));
    }

    @Test
    void collaboratorShouldReceiveNotFoundForUnlinkedBucketDetail() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");

        mockMvc.perform(get(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH + "/" + bucket.getBucketId()));
    }

    @Test
    void collaboratorShouldReceiveNotFoundForBucketLinkedOnlyToInaccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio nascosto");

        linkBucketToAccount(bucket, inaccessibleAccount);

        mockMvc.perform(get(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH + "/" + bucket.getBucketId()));
    }

    @Test
    void findBucketShouldReturnNotFoundForAnotherUserGroupBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Bucket otherGroupBucket = createBucket(otherOwner.getUserGroup(), "Portafoglio altro gruppo");

        mockMvc.perform(get(BUCKETS_PATH + "/" + otherGroupBucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH + "/" + otherGroupBucket.getBucketId()));
    }

    @Test
    void findBucketShouldReturnClosedBucketDetail() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio chiuso");

        closeBucket(bucket);

        mockMvc.perform(get(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketName").value("Portafoglio chiuso"))
                .andExpect(jsonPath("$.bucketClosedAt").exists());
    }

    @Test
    void findBucketsShouldNotReturnClosedBuckets() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket activeBucket = createBucket(owner.getUserGroup(), "A portafoglio attivo");
        Bucket closedBucket = createBucket(owner.getUserGroup(), "B portafoglio chiuso");

        closeBucket(closedBucket);

        mockMvc.perform(get(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bucketId").value(activeBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketName").value("A portafoglio attivo"));
    }

    @Test
    void findBucketsShouldReturnEmptyListWhenUserGroupHasNoBuckets() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        mockMvc.perform(get(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findBucketsShouldOnlyReturnBucketsFromCurrentUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Bucket ownBucket = createBucket(owner.getUserGroup(), "Portafoglio mio");
        createBucket(otherOwner.getUserGroup(), "Portafoglio altro gruppo");

        mockMvc.perform(get(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bucketId").value(ownBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketName").value("Portafoglio mio"));
    }

    @Test
    void collaboratorShouldReceiveEmptyListWhenNoBucketsAreVisible() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        Bucket unlinkedBucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");
        Bucket hiddenBucket = createBucket(owner.getUserGroup(), "Portafoglio nascosto");

        linkBucketToAccount(hiddenBucket, inaccessibleAccount);

        mockMvc.perform(get(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(unlinkedBucket.getBucketId()).isNotNull();
    }

    @Test
    void ownerShouldFindBucketDetailWithAllLinkedAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account firstAccount = createAccount(owner.getUserGroup(), "Conto principale");
        Account secondAccount = createAccount(owner.getUserGroup(), "Conto secondario");

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio multi conto");

        linkBucketToAccount(bucket, firstAccount);
        linkBucketToAccount(bucket, secondAccount);

        mockMvc.perform(get(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.accountIds.length()").value(2))
                .andExpect(jsonPath("$.accountIds[0]").value(firstAccount.getAccountId().toString()))
                .andExpect(jsonPath("$.accountIds[1]").value(secondAccount.getAccountId().toString()));
    }

    @Test
    void updateBucketShouldRequireAuthentication() throws Exception {
        mockMvc.perform(patch(BUCKETS_PATH + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Nuovo nome"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldUpdateBucketNameAndDescription() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Vecchio portafoglio");

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "  Nuovo portafoglio  ",
                                  "bucketDescription": "  Nuova descrizione  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketName").value("Nuovo portafoglio"))
                .andExpect(jsonPath("$.bucketDescription").value("Nuova descrizione"))
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty())
                .andExpect(jsonPath("$.userGroupId").value(owner.getUserGroup().getUserGroupId().toString()));

        Bucket updatedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketName()).isEqualTo("Nuovo portafoglio");
        assertThat(updatedBucket.getBucketDescription()).isEqualTo("Nuova descrizione");
    }

    @Test
    void ownerShouldUpdateOnlyDescriptionWhenNameIsMissing() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = bucketRepository.saveAndFlush(Bucket.create(
                "Portafoglio invariato",
                "Vecchia descrizione",
                owner.getUserGroup()
        ));

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketDescription": "Descrizione aggiornata"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketName").value("Portafoglio invariato"))
                .andExpect(jsonPath("$.bucketDescription").value("Descrizione aggiornata"));

        Bucket updatedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketName()).isEqualTo("Portafoglio invariato");
        assertThat(updatedBucket.getBucketDescription()).isEqualTo("Descrizione aggiornata");
    }

    @Test
    void ownerShouldUpdateOnlyNameWhenDescriptionIsMissing() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = bucketRepository.saveAndFlush(Bucket.create(
                "Vecchio nome",
                "Descrizione invariata",
                owner.getUserGroup()
        ));

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Nuovo nome"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketName").value("Nuovo nome"))
                .andExpect(jsonPath("$.bucketDescription").value("Descrizione invariata"));

        Bucket updatedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketName()).isEqualTo("Nuovo nome");
        assertThat(updatedBucket.getBucketDescription()).isEqualTo("Descrizione invariata");
    }

    @Test
    void updateBucketShouldClearDescriptionWhenBlankDescriptionIsProvided() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = bucketRepository.saveAndFlush(Bucket.create(
                "Portafoglio",
                "Descrizione da cancellare",
                owner.getUserGroup()
        ));

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketDescription": "     "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketName").value("Portafoglio"))
                .andExpect(jsonPath("$.bucketDescription").doesNotExist());

        Bucket updatedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketDescription()).isNull();
    }

    @Test
    void updateBucketShouldDoNothingWhenBodyHasNoUpdatableFields() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = bucketRepository.saveAndFlush(Bucket.create(
                "Portafoglio invariato",
                "Descrizione invariata",
                owner.getUserGroup()
        ));

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketName").value("Portafoglio invariato"))
                .andExpect(jsonPath("$.bucketDescription").value("Descrizione invariata"));

        Bucket updatedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketName()).isEqualTo("Portafoglio invariato");
        assertThat(updatedBucket.getBucketDescription()).isEqualTo("Descrizione invariata");
    }

    @Test
    void superCollaboratorShouldUpdateAnyActiveGroupBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User superCollaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.SUPER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio owner");

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio aggiornato da super"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketName").value("Portafoglio aggiornato da super"));

        Bucket updatedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketName()).isEqualTo("Portafoglio aggiornato da super");
    }

    @Test
    void viewerCollaboratorShouldUpdateBucketLinkedToAccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account anotherAccount = createAccount(owner.getUserGroup(), "Altro conto");

        grantAccountAccess(accessibleAccount, viewer);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio viewer");

        linkBucketToAccount(bucket, accessibleAccount);
        linkBucketToAccount(bucket, anotherAccount);

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio aggiornato da viewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketName").value("Portafoglio aggiornato da viewer"))
                .andExpect(jsonPath("$.accountIds.length()").value(2))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.getAccountId().toString()))
                .andExpect(jsonPath("$.accountIds[1]").value(anotherAccount.getAccountId().toString()));

        Bucket updatedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketName()).isEqualTo("Portafoglio aggiornato da viewer");
    }

    @Test
    void viewerCollaboratorShouldReceiveNotFoundWhenBucketIsNotLinkedToAccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Nome non applicato"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH + "/" + bucket.getBucketId()));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketName()).isEqualTo("Portafoglio scollegato");
    }

    @Test
    void collaboratorShouldUpdateBucketLinkedToAccessibleAccountAndReceiveOnlyAccessibleAccountIds() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio collaborator");

        linkBucketToAccount(bucket, accessibleAccount);
        linkBucketToAccount(bucket, inaccessibleAccount);

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Portafoglio aggiornato da collaborator",
                                  "bucketDescription": "Descrizione collaborator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketName").value("Portafoglio aggiornato da collaborator"))
                .andExpect(jsonPath("$.bucketDescription").value("Descrizione collaborator"))
                .andExpect(jsonPath("$.accountIds.length()").value(1))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.getAccountId().toString()));

        Bucket updatedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketName()).isEqualTo("Portafoglio aggiornato da collaborator");
        assertThat(updatedBucket.getBucketDescription()).isEqualTo("Descrizione collaborator");
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenUpdatingUnlinkedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Nome non applicato"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH + "/" + bucket.getBucketId()));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketName()).isEqualTo("Portafoglio scollegato");
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenUpdatingBucketLinkedOnlyToInaccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio nascosto");

        linkBucketToAccount(bucket, inaccessibleAccount);

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Nome non applicato"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH + "/" + bucket.getBucketId()));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketName()).isEqualTo("Portafoglio nascosto");
    }

    @Test
    void updateBucketShouldAllowNameUsedOnlyByClosedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket closedBucket = createBucket(owner.getUserGroup(), "Fondo vacanze");
        closeBucket(closedBucket);

        Bucket bucketToUpdate = createBucket(owner.getUserGroup(), "Portafoglio da rinominare");

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucketToUpdate.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "  fondo    VACANZE  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketName").value("fondo    VACANZE"));

        Bucket updatedBucket = bucketRepository.findById(bucketToUpdate.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketName()).isEqualTo("fondo    VACANZE");
    }

    @Test
    void updateBucketShouldAllowSameNormalizedNameInDifferentUserGroups() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        createBucket(otherOwner.getUserGroup(), "Fondo vacanze");

        Bucket bucketToUpdate = createBucket(owner.getUserGroup(), "Portafoglio da rinominare");

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucketToUpdate.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "  fondo    VACANZE  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketName").value("fondo    VACANZE"));

        Bucket updatedBucket = bucketRepository.findById(bucketToUpdate.getBucketId()).orElseThrow();

        assertThat(updatedBucket.getBucketName()).isEqualTo("fondo    VACANZE");
    }

    @Test
    void updateBucketShouldNotPartiallyUpdateDescriptionWhenNameIsDuplicate() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        createBucket(owner.getUserGroup(), "Fondo vacanze");

        Bucket bucketToUpdate = bucketRepository.saveAndFlush(Bucket.create(
                "Portafoglio da rinominare",
                "Descrizione originale",
                owner.getUserGroup()
        ));

        mockMvc.perform(patch(BUCKETS_PATH + "/" + bucketToUpdate.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Fondo vacanze",
                                  "bucketDescription": "Descrizione che non deve essere salvata"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.nameAlreadyExists"));

        Bucket unchangedBucket = bucketRepository.findById(bucketToUpdate.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketName()).isEqualTo("Portafoglio da rinominare");
        assertThat(unchangedBucket.getBucketDescription()).isEqualTo("Descrizione originale");
    }

    @Test
    void collaboratorShouldReceiveNotFoundBeforeDuplicateNameCheckWhenBucketIsNotVisible() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        createBucket(owner.getUserGroup(), "Nome sensibile");

        Bucket hiddenBucket = createBucket(owner.getUserGroup(), "Portafoglio nascosto");

        mockMvc.perform(patch(BUCKETS_PATH + "/" + hiddenBucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucketName": "Nome sensibile"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH + "/" + hiddenBucket.getBucketId()));

        Bucket unchangedBucket = bucketRepository.findById(hiddenBucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketName()).isEqualTo("Portafoglio nascosto");
    }

    @Test
    void linkBucketAccountShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post(BUCKETS_PATH + "/" + UUID.randomUUID() + "/accounts/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unlinkBucketAccountShouldRequireAuthentication() throws Exception {
        mockMvc.perform(delete(BUCKETS_PATH + "/" + UUID.randomUUID() + "/accounts/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldLinkAnyGroupAccountToBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(post(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void superCollaboratorShouldLinkAnyGroupAccountToBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User superCollaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.SUPER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(post(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void viewerCollaboratorShouldLinkAccessibleAccountToAnyVisibleGroupBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");
        Account account = createAccount(owner.getUserGroup(), "Conto accessibile");

        grantAccountAccess(account, viewer);

        mockMvc.perform(post(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void viewerCollaboratorShouldNotLinkInaccessibleAccountToBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        mockMvc.perform(post(bucketAccountPath(bucket, inaccessibleAccount))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, inaccessibleAccount)));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                inaccessibleAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isFalse();
    }

    @Test
    void collaboratorShouldLinkAccessibleAccountToVisibleBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account firstAccessibleAccount = createAccount(owner.getUserGroup(), "Primo conto accessibile");
        Account secondAccessibleAccount = createAccount(owner.getUserGroup(), "Secondo conto accessibile");

        grantAccountAccess(firstAccessibleAccount, collaborator);
        grantAccountAccess(secondAccessibleAccount, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio visibile");

        linkBucketToAccount(bucket, firstAccessibleAccount);

        mockMvc.perform(post(bucketAccountPath(bucket, secondAccessibleAccount))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                secondAccessibleAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void collaboratorShouldNotLinkAccountToUnlinkedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");
        Account account = createAccount(owner.getUserGroup(), "Conto accessibile");

        grantAccountAccess(account, collaborator);

        mockMvc.perform(post(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, account)));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isFalse();
    }

    @Test
    void collaboratorShouldNotLinkInaccessibleAccountToVisibleBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio visibile");

        linkBucketToAccount(bucket, accessibleAccount);

        mockMvc.perform(post(bucketAccountPath(bucket, inaccessibleAccount))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, inaccessibleAccount)));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                inaccessibleAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isFalse();
    }

    @Test
    void linkBucketAccountShouldBeNoOpWhenLinkAlreadyExists() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        linkBucketToAccount(bucket, account);

        mockMvc.perform(post(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.countByBucket_BucketId(bucket.getBucketId()))
                .isEqualTo(1);
    }

    @Test
    void linkBucketAccountShouldReturnNotFoundWhenBucketBelongsToAnotherUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Bucket otherGroupBucket = createBucket(otherOwner.getUserGroup(), "Portafoglio altro gruppo");
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(post(bucketAccountPath(otherGroupBucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(otherGroupBucket, account)));

        assertThat(bucketAccountRepository.countByBucket_BucketId(otherGroupBucket.getBucketId()))
                .isZero();
    }

    @Test
    void linkBucketAccountShouldReturnNotFoundWhenAccountBelongsToAnotherUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account otherGroupAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");

        mockMvc.perform(post(bucketAccountPath(bucket, otherGroupAccount))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, otherGroupAccount)));

        assertThat(bucketAccountRepository.countByBucket_BucketId(bucket.getBucketId()))
                .isZero();
    }

    @Test
    void linkBucketAccountShouldReturnNotFoundWhenBucketIsClosed() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio chiuso");
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        closeBucket(bucket);

        mockMvc.perform(post(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, account)));

        assertThat(bucketAccountRepository.countByBucket_BucketId(bucket.getBucketId()))
                .isZero();
    }

    @Test
    void ownerShouldUnlinkExistingBucketAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        linkBucketToAccount(bucket, account);

        mockMvc.perform(delete(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isFalse();
    }

    @Test
    void unlinkBucketAccountShouldBeNoOpWhenLinkDoesNotExist() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(delete(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.countByBucket_BucketId(bucket.getBucketId()))
                .isZero();
    }

    @Test
    void viewerCollaboratorShouldUnlinkAccessibleAccountFromBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account account = createAccount(owner.getUserGroup(), "Conto accessibile");

        grantAccountAccess(account, viewer);
        linkBucketToAccount(bucket, account);

        mockMvc.perform(delete(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isFalse();
    }

    @Test
    void viewerCollaboratorShouldNotUnlinkInaccessibleAccountFromBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        linkBucketToAccount(bucket, inaccessibleAccount);

        mockMvc.perform(delete(bucketAccountPath(bucket, inaccessibleAccount))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, inaccessibleAccount)));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                inaccessibleAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void collaboratorShouldUnlinkAccessibleAccountFromVisibleBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account account = createAccount(owner.getUserGroup(), "Conto accessibile");

        grantAccountAccess(account, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio visibile");

        linkBucketToAccount(bucket, account);

        mockMvc.perform(delete(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isFalse();
    }

    @Test
    void collaboratorShouldNotUnlinkInaccessibleAccountFromMixedVisibleBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio misto");

        linkBucketToAccount(bucket, accessibleAccount);
        linkBucketToAccount(bucket, inaccessibleAccount);

        mockMvc.perform(delete(bucketAccountPath(bucket, inaccessibleAccount))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, inaccessibleAccount)));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                accessibleAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                inaccessibleAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void collaboratorShouldNotUnlinkAccountFromUnlinkedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");
        Account account = createAccount(owner.getUserGroup(), "Conto accessibile");

        grantAccountAccess(account, collaborator);

        mockMvc.perform(delete(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, account)));
    }

    @Test
    void unlinkBucketAccountShouldReturnNotFoundWhenBucketBelongsToAnotherUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Bucket otherGroupBucket = createBucket(otherOwner.getUserGroup(), "Portafoglio altro gruppo");
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(delete(bucketAccountPath(otherGroupBucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(otherGroupBucket, account)));
    }

    @Test
    void unlinkBucketAccountShouldReturnNotFoundWhenAccountBelongsToAnotherUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        Account otherGroupAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");

        mockMvc.perform(delete(bucketAccountPath(bucket, otherGroupAccount))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, otherGroupAccount)));
    }

    @Test
    void unlinkBucketAccountShouldReturnNotFoundWhenBucketIsClosed() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio chiuso");
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        linkBucketToAccount(bucket, account);
        closeBucket(bucket);

        mockMvc.perform(delete(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, account)));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void linkBucketAccountShouldReturnNotFoundWhenBucketDoesNotExist() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto principale");
        UUID missingBucketId = UUID.randomUUID();

        String path = BUCKETS_PATH + "/" + missingBucketId + "/accounts/" + account.getAccountId();

        mockMvc.perform(post(path)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(path));
    }

    @Test
    void linkBucketAccountShouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        UUID missingAccountId = UUID.randomUUID();

        String path = BUCKETS_PATH + "/" + bucket.getBucketId() + "/accounts/" + missingAccountId;

        mockMvc.perform(post(path)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(path));

        assertThat(bucketAccountRepository.countByBucket_BucketId(bucket.getBucketId()))
                .isZero();
    }

    @Test
    void unlinkBucketAccountShouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio");
        UUID missingAccountId = UUID.randomUUID();

        String path = BUCKETS_PATH + "/" + bucket.getBucketId() + "/accounts/" + missingAccountId;

        mockMvc.perform(delete(path)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.path").value(path));
    }

    @Test
    void collaboratorShouldReceiveBucketNotFoundBeforeAccountCheckWhenLinkingToHiddenBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Bucket hiddenBucket = createBucket(owner.getUserGroup(), "Portafoglio nascosto");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        String path = bucketAccountPath(hiddenBucket, inaccessibleAccount);

        mockMvc.perform(post(path)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(path));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                hiddenBucket.getBucketId(),
                inaccessibleAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isFalse();
    }

    @Test
    void collaboratorShouldReceiveBucketNotFoundBeforeAccountCheckWhenUnlinkingFromHiddenBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Bucket hiddenBucket = createBucket(owner.getUserGroup(), "Portafoglio nascosto");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        linkBucketToAccount(hiddenBucket, inaccessibleAccount);

        String path = bucketAccountPath(hiddenBucket, inaccessibleAccount);

        mockMvc.perform(delete(path)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(path));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                hiddenBucket.getBucketId(),
                inaccessibleAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void collaboratorShouldNoOpWhenUnlinkingAccessibleAccountNotLinkedToVisibleBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account linkedAccessibleAccount = createAccount(owner.getUserGroup(), "Conto collegato");
        Account notLinkedAccessibleAccount = createAccount(owner.getUserGroup(), "Conto non collegato");

        grantAccountAccess(linkedAccessibleAccount, collaborator);
        grantAccountAccess(notLinkedAccessibleAccount, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio visibile");

        linkBucketToAccount(bucket, linkedAccessibleAccount);

        mockMvc.perform(delete(bucketAccountPath(bucket, notLinkedAccessibleAccount))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                linkedAccessibleAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();

        assertThat(bucketAccountRepository.countByBucket_BucketId(bucket.getBucketId()))
                .isEqualTo(1);
    }

    @Test
    void findBucketsShouldReturnOnlyClosedBucketsWhenStatusClosedForOwner() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket activeBucket = createBucket(owner.getUserGroup(), "A portafoglio attivo");
        Bucket closedBucket = createBucket(owner.getUserGroup(), "B portafoglio chiuso");

        closeBucket(closedBucket);

        mockMvc.perform(get(BUCKETS_PATH)
                        .param("status", "CLOSED")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bucketId").value(closedBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketName").value("B portafoglio chiuso"))
                .andExpect(jsonPath("$[0].bucketClosedAt").exists());

        assertThat(activeBucket.getBucketId()).isNotEqualTo(closedBucket.getBucketId());
    }

    @Test
    void findBucketsShouldReturnActiveAndClosedBucketsWhenStatusAllForOwner() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket activeBucket = createBucket(owner.getUserGroup(), "A portafoglio attivo");
        Bucket closedBucket = createBucket(owner.getUserGroup(), "B portafoglio chiuso");

        closeBucket(closedBucket);

        mockMvc.perform(get(BUCKETS_PATH)
                        .param("status", "ALL")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bucketId").value(activeBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketName").value("A portafoglio attivo"))
                .andExpect(jsonPath("$[0].bucketClosedAt").doesNotExist())
                .andExpect(jsonPath("$[1].bucketId").value(closedBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[1].bucketName").value("B portafoglio chiuso"))
                .andExpect(jsonPath("$[1].bucketClosedAt").exists());
    }

    @Test
    void findBucketsShouldUseActiveStatusByDefault() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket activeBucket = createBucket(owner.getUserGroup(), "A portafoglio attivo");
        Bucket closedBucket = createBucket(owner.getUserGroup(), "B portafoglio chiuso");

        closeBucket(closedBucket);

        mockMvc.perform(get(BUCKETS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bucketId").value(activeBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketName").value("A portafoglio attivo"))
                .andExpect(jsonPath("$[0].bucketClosedAt").doesNotExist());
    }

    @Test
    void findBucketsShouldAcceptCaseInsensitiveStatusFilter() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket closedBucket = createBucket(owner.getUserGroup(), "Portafoglio chiuso");

        closeBucket(closedBucket);

        mockMvc.perform(get(BUCKETS_PATH)
                        .param("status", "closed")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bucketId").value(closedBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketClosedAt").exists());
    }

    @Test
    void findBucketsShouldRejectInvalidStatusFilter() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        mockMvc.perform(get(BUCKETS_PATH)
                        .param("status", "ARCHIVED")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.bucket.status.invalid"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH));
    }

    @Test
    void collaboratorShouldFindOnlyVisibleClosedBucketsWhenStatusClosed() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket activeVisibleBucket = createBucket(owner.getUserGroup(), "A portafoglio attivo visibile");
        Bucket closedVisibleBucket = createBucket(owner.getUserGroup(), "B portafoglio chiuso visibile");
        Bucket closedHiddenBucket = createBucket(owner.getUserGroup(), "C portafoglio chiuso nascosto");
        Bucket closedUnlinkedBucket = createBucket(owner.getUserGroup(), "D portafoglio chiuso scollegato");

        linkBucketToAccount(activeVisibleBucket, accessibleAccount);
        linkBucketToAccount(closedVisibleBucket, accessibleAccount);
        linkBucketToAccount(closedHiddenBucket, inaccessibleAccount);

        closeBucket(closedVisibleBucket);
        closeBucket(closedHiddenBucket);
        closeBucket(closedUnlinkedBucket);

        mockMvc.perform(get(BUCKETS_PATH)
                        .param("status", "CLOSED")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bucketId").value(closedVisibleBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketName").value("B portafoglio chiuso visibile"))
                .andExpect(jsonPath("$[0].bucketClosedAt").exists())
                .andExpect(jsonPath("$[0].accountIds.length()").value(1))
                .andExpect(jsonPath("$[0].accountIds[0]").value(accessibleAccount.getAccountId().toString()));
    }

    @Test
    void collaboratorShouldFindOnlyVisibleActiveAndClosedBucketsWhenStatusAll() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket activeVisibleBucket = createBucket(owner.getUserGroup(), "A portafoglio attivo visibile");
        Bucket closedVisibleBucket = createBucket(owner.getUserGroup(), "B portafoglio chiuso visibile");
        Bucket activeHiddenBucket = createBucket(owner.getUserGroup(), "C portafoglio attivo nascosto");
        Bucket closedHiddenBucket = createBucket(owner.getUserGroup(), "D portafoglio chiuso nascosto");
        Bucket unlinkedBucket = createBucket(owner.getUserGroup(), "E portafoglio scollegato");

        linkBucketToAccount(activeVisibleBucket, accessibleAccount);
        linkBucketToAccount(closedVisibleBucket, accessibleAccount);
        linkBucketToAccount(activeHiddenBucket, inaccessibleAccount);
        linkBucketToAccount(closedHiddenBucket, inaccessibleAccount);

        closeBucket(closedVisibleBucket);
        closeBucket(closedHiddenBucket);

        mockMvc.perform(get(BUCKETS_PATH)
                        .param("status", "ALL")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bucketId").value(activeVisibleBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketName").value("A portafoglio attivo visibile"))
                .andExpect(jsonPath("$[0].bucketClosedAt").doesNotExist())
                .andExpect(jsonPath("$[0].accountIds.length()").value(1))
                .andExpect(jsonPath("$[0].accountIds[0]").value(accessibleAccount.getAccountId().toString()))
                .andExpect(jsonPath("$[1].bucketId").value(closedVisibleBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[1].bucketName").value("B portafoglio chiuso visibile"))
                .andExpect(jsonPath("$[1].bucketClosedAt").exists())
                .andExpect(jsonPath("$[1].accountIds.length()").value(1))
                .andExpect(jsonPath("$[1].accountIds[0]").value(accessibleAccount.getAccountId().toString()));

        assertThat(unlinkedBucket.getBucketId()).isNotNull();
    }

    @Test
    void findBucketsShouldUseActiveStatusWhenStatusIsBlank() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket activeBucket = createBucket(owner.getUserGroup(), "A portafoglio attivo");
        Bucket closedBucket = createBucket(owner.getUserGroup(), "B portafoglio chiuso");

        closeBucket(closedBucket);

        mockMvc.perform(get(BUCKETS_PATH)
                        .param("status", "   ")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bucketId").value(activeBucket.getBucketId().toString()))
                .andExpect(jsonPath("$[0].bucketClosedAt").doesNotExist());
    }

    @Test
    void collaboratorShouldFindClosedBucketDetailWhenLinkedToAccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio chiuso visibile");

        linkBucketToAccount(bucket, accessibleAccount);
        linkBucketToAccount(bucket, inaccessibleAccount);
        closeBucket(bucket);

        mockMvc.perform(get(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketName").value("Portafoglio chiuso visibile"))
                .andExpect(jsonPath("$.bucketClosedAt").exists())
                .andExpect(jsonPath("$.accountIds.length()").value(1))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.getAccountId().toString()));
    }

    @Test
    void closeBucketShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post(BUCKETS_PATH + "/" + UUID.randomUUID() + "/close"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldCloseActiveBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio da chiudere");

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketName").value("Portafoglio da chiudere"))
                .andExpect(jsonPath("$.bucketClosedAt").exists())
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void superCollaboratorShouldCloseActiveGroupBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User superCollaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.SUPER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio super");

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void viewerCollaboratorShouldCloseBucketLinkedToAccessibleAccountAndReceiveAllAccountIds() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account anotherAccount = createAccount(owner.getUserGroup(), "Altro conto");

        grantAccountAccess(accessibleAccount, viewer);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio viewer");

        linkBucketToAccount(bucket, accessibleAccount);
        linkBucketToAccount(bucket, anotherAccount);

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists())
                .andExpect(jsonPath("$.accountIds.length()").value(2))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.getAccountId().toString()))
                .andExpect(jsonPath("$.accountIds[1]").value(anotherAccount.getAccountId().toString()));

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void viewerCollaboratorShouldReceiveNotFoundWhenClosingUnlinkedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void collaboratorShouldCloseBucketLinkedToAccessibleAccountAndReceiveOnlyAccessibleAccountIds() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio collaborator");

        linkBucketToAccount(bucket, accessibleAccount);
        linkBucketToAccount(bucket, inaccessibleAccount);

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists())
                .andExpect(jsonPath("$.accountIds.length()").value(1))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.getAccountId().toString()));

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenClosingUnlinkedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenClosingBucketLinkedOnlyToInaccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio nascosto");

        linkBucketToAccount(bucket, inaccessibleAccount);

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldReturnNotFoundForAnotherUserGroupBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Bucket otherGroupBucket = createBucket(otherOwner.getUserGroup(), "Portafoglio altro gruppo");

        mockMvc.perform(post(bucketClosePath(otherGroupBucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(otherGroupBucket)));

        Bucket unchangedBucket = bucketRepository.findById(otherGroupBucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldReturnNotFoundForAlreadyClosedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio già chiuso");

        closeBucket(bucket);

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));
    }

    @Test
    void closeBucketShouldReturnNotFoundForMissingBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UUID missingBucketId = UUID.randomUUID();

        String path = bucketClosePath(missingBucketId);

        mockMvc.perform(post(path)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(path));
    }

    @Test
    void reopenBucketShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post(BUCKETS_PATH + "/" + UUID.randomUUID() + "/reopen"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldReopenClosedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio da riaprire");

        closeBucket(bucket);

        mockMvc.perform(post(bucketReopenPath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketName").value("Portafoglio da riaprire"))
                .andExpect(jsonPath("$.bucketClosedAt").doesNotExist())
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty());

        Bucket reopenedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(reopenedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void superCollaboratorShouldReopenClosedGroupBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User superCollaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.SUPER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio super");

        closeBucket(bucket);

        mockMvc.perform(post(bucketReopenPath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").doesNotExist());

        Bucket reopenedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(reopenedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void viewerCollaboratorShouldReopenClosedBucketLinkedToAccessibleAccountAndReceiveAllAccountIds() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account anotherAccount = createAccount(owner.getUserGroup(), "Altro conto");

        grantAccountAccess(accessibleAccount, viewer);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio viewer");

        linkBucketToAccount(bucket, accessibleAccount);
        linkBucketToAccount(bucket, anotherAccount);
        closeBucket(bucket);

        mockMvc.perform(post(bucketReopenPath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").doesNotExist())
                .andExpect(jsonPath("$.accountIds.length()").value(2))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.getAccountId().toString()))
                .andExpect(jsonPath("$.accountIds[1]").value(anotherAccount.getAccountId().toString()));

        Bucket reopenedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(reopenedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void viewerCollaboratorShouldReceiveNotFoundWhenReopeningClosedUnlinkedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");

        closeBucket(bucket);

        mockMvc.perform(post(bucketReopenPath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void collaboratorShouldReopenClosedBucketLinkedToAccessibleAccountAndReceiveOnlyAccessibleAccountIds() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");
        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio collaborator");

        linkBucketToAccount(bucket, accessibleAccount);
        linkBucketToAccount(bucket, inaccessibleAccount);
        closeBucket(bucket);

        mockMvc.perform(post(bucketReopenPath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").doesNotExist())
                .andExpect(jsonPath("$.accountIds.length()").value(1))
                .andExpect(jsonPath("$.accountIds[0]").value(accessibleAccount.getAccountId().toString()));

        Bucket reopenedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(reopenedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenReopeningClosedUnlinkedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio scollegato");

        closeBucket(bucket);

        mockMvc.perform(post(bucketReopenPath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenReopeningBucketLinkedOnlyToInaccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio nascosto");

        linkBucketToAccount(bucket, inaccessibleAccount);
        closeBucket(bucket);

        mockMvc.perform(post(bucketReopenPath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void reopenBucketShouldReturnNotFoundForActiveBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio attivo");

        mockMvc.perform(post(bucketReopenPath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void reopenBucketShouldReturnNotFoundForAnotherUserGroupBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        Bucket otherGroupBucket = createBucket(otherOwner.getUserGroup(), "Portafoglio altro gruppo");

        closeBucket(otherGroupBucket);

        mockMvc.perform(post(bucketReopenPath(otherGroupBucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(otherGroupBucket)));

        Bucket unchangedBucket = bucketRepository.findById(otherGroupBucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void reopenBucketShouldReturnNotFoundForMissingBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UUID missingBucketId = UUID.randomUUID();

        String path = bucketReopenPath(missingBucketId);

        mockMvc.perform(post(path)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(path));
    }

    @Test
    void ownerShouldReceiveExplicitDuplicateNameErrorWhenReopeningBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket closedBucket = createBucket(owner.getUserGroup(), "  fondo    VACANZE  ");

        closeBucket(closedBucket);

        createBucket(owner.getUserGroup(), "Fondo vacanze");

        mockMvc.perform(post(bucketReopenPath(closedBucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.nameAlreadyExists"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(closedBucket)));

        Bucket unchangedBucket = bucketRepository.findById(closedBucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void viewerCollaboratorShouldReceiveExplicitDuplicateNameErrorWhenReopeningBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");

        grantAccountAccess(accessibleAccount, viewer);

        Bucket closedBucket = createBucket(owner.getUserGroup(), "  fondo    VACANZE  ");

        linkBucketToAccount(closedBucket, accessibleAccount);
        closeBucket(closedBucket);

        createBucket(owner.getUserGroup(), "Fondo vacanze");

        mockMvc.perform(post(bucketReopenPath(closedBucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.nameAlreadyExists"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(closedBucket)));

        Bucket unchangedBucket = bucketRepository.findById(closedBucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void collaboratorShouldNotLearnDuplicateNameExistenceWhenReopeningBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account accessibleAccount = createAccount(owner.getUserGroup(), "Conto accessibile");

        grantAccountAccess(accessibleAccount, collaborator);

        Bucket closedBucket = createBucket(owner.getUserGroup(), "  vacanza    SEGRETA  ");

        linkBucketToAccount(closedBucket, accessibleAccount);
        closeBucket(closedBucket);

        createBucket(owner.getUserGroup(), "Vacanza segreta");

        mockMvc.perform(post(bucketReopenPath(closedBucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.bucket.nameNotAllowed"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(closedBucket)));

        Bucket unchangedBucket = bucketRepository.findById(closedBucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void reopenBucketShouldAllowSameNormalizedNameInDifferentUserGroups() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User otherOwner = createVerifiedUser(UserRole.OWNER);

        createBucket(otherOwner.getUserGroup(), "Fondo vacanze");

        Bucket closedBucket = createBucket(owner.getUserGroup(), "  fondo    VACANZE  ");

        closeBucket(closedBucket);

        mockMvc.perform(post(bucketReopenPath(closedBucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(closedBucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").doesNotExist());

        Bucket reopenedBucket = bucketRepository.findById(closedBucket.getBucketId()).orElseThrow();

        assertThat(reopenedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void viewerCollaboratorShouldReceiveNotFoundWhenClosingBucketLinkedOnlyToInaccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio non operativo");

        linkBucketToAccount(bucket, inaccessibleAccount);

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void viewerCollaboratorShouldReceiveNotFoundWhenReopeningBucketLinkedOnlyToInaccessibleAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User viewer = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.VIEWER_COLLABORATOR
        );

        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio non operativo");

        linkBucketToAccount(bucket, inaccessibleAccount);
        closeBucket(bucket);

        mockMvc.perform(post(bucketReopenPath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void collaboratorShouldReceiveNotFoundBeforeDuplicateNameCheckWhenReopeningHiddenBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User collaborator = createVerifiedUserInGroup(
                owner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account inaccessibleAccount = createAccount(owner.getUserGroup(), "Conto non accessibile");

        Bucket hiddenBucket = createBucket(owner.getUserGroup(), "Vacanza segreta");

        linkBucketToAccount(hiddenBucket, inaccessibleAccount);
        closeBucket(hiddenBucket);

        createBucket(owner.getUserGroup(), "  vacanza    SEGRETA  ");

        mockMvc.perform(post(bucketReopenPath(hiddenBucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(bucketReopenPath(hiddenBucket)));

        Bucket unchangedBucket = bucketRepository.findById(hiddenBucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void unlinkBucketAccountShouldRejectLinkUsedByTransaction() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto usato da transaction");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket usato da transaction");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria transaction bucket account"
        );

        insertTransactionUsingBucketAccount(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId
        );

        mockMvc.perform(delete(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucketAccount.alreadyUsed"))
                .andExpect(jsonPath("$.message").value(
                        "Il collegamento tra portafoglio e conto non può essere rimosso perché è già stato usato."
                ))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, account)));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void unlinkBucketAccountShouldRejectLinkUsedByRecurringTransactionDetailsHistory() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto usato da ricorrente");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket usato da ricorrente");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria recurring bucket account"
        );

        insertRecurringTransactionDetailsHistoryUsingBucketAccount(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId
        );

        mockMvc.perform(delete(bucketAccountPath(bucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucketAccount.alreadyUsed"))
                .andExpect(jsonPath("$.message").value(
                        "Il collegamento tra portafoglio e conto non può essere rimosso perché è già stato usato."
                ))
                .andExpect(jsonPath("$.path").value(bucketAccountPath(bucket, account)));

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();
    }

    @Test
    void unlinkBucketAccountShouldOnlyConsiderExactBucketAccountPairUsage() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account usedAccount = createAccount(owner.getUserGroup(), "Conto usato");
        Account removableAccount = createAccount(owner.getUserGroup(), "Conto removibile");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket con due conti");

        linkBucketToAccount(bucket, usedAccount);
        linkBucketToAccount(bucket, removableAccount);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria usage exact pair"
        );

        insertTransactionUsingBucketAccount(
                owner.getUserGroup(),
                usedAccount,
                bucket,
                categoryId
        );

        mockMvc.perform(delete(bucketAccountPath(bucket, removableAccount))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                usedAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucket.getBucketId(),
                removableAccount.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isFalse();
    }

    @Test
    void unlinkBucketAccountShouldNotBeBlockedBySameAccountUsedWithDifferentBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto condiviso");
        Bucket usedBucket = createBucket(owner.getUserGroup(), "Bucket usato");
        Bucket removableBucket = createBucket(owner.getUserGroup(), "Bucket removibile");

        linkBucketToAccount(usedBucket, account);
        linkBucketToAccount(removableBucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria same account different bucket"
        );

        insertTransactionUsingBucketAccount(
                owner.getUserGroup(),
                account,
                usedBucket,
                categoryId
        );

        mockMvc.perform(delete(bucketAccountPath(removableBucket, account))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                usedBucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isTrue();

        assertThat(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                removableBucket.getBucketId(),
                account.getAccountId(),
                owner.getUserGroup().getUserGroupId()
        )).isFalse();
    }

    @Test
    void closeBucketShouldRejectBucketWithPositiveResidualBalanceFromPersistedTransaction() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket positivo persisted");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket positivo persisted");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria bucket positivo persisted"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-200.00"),
                false,
                true,
                today(),
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.balanceMustBeZero"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldRejectBucketWithPositiveResidualBalanceFromProjectedRecurringTransaction() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket positivo recurring");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket positivo recurring");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria bucket positivo recurring"
        );

        insertDailyRecurringTransactionUsingBucket(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                today(),
                new BigDecimal("-200.00"),
                false,
                true,
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.balanceMustBeZero"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldAllowBucketWithZeroResidualBalance() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket zero");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket zero");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria bucket zero"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-200.00"),
                false,
                true,
                today(),
                false,
                null
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-200.00"),
                true,
                false,
                today(),
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldRejectBucketWithNegativeResidualBalance() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket negativo");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket negativo");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria bucket negativo"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-50.00"),
                true,
                false,
                today(),
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.balanceMustBeZero"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldIgnoreSimulatedPersistedTransactions() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket simulated persisted");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket simulated persisted");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria simulated persisted"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.getUserGroup(),
                "Simulazione persisted bucket"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-200.00"),
                false,
                true,
                today(),
                true,
                simulationGroupId
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldIgnoreSimulatedRecurringTransactions() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket simulated recurring");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket simulated recurring");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria simulated recurring"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.getUserGroup(),
                "Simulazione recurring bucket"
        );

        insertDailyRecurringTransactionUsingBucket(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                today(),
                new BigDecimal("-200.00"),
                false,
                true,
                true,
                simulationGroupId
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldNotDoubleCountAlreadyConfirmedRecurringOccurrence() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket recurring dedup");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket recurring dedup");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria recurring dedup"
        );

        LocalDate logicalDate = today();

        UUID recurringTransactionId = insertDailyRecurringTransactionUsingBucket(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                logicalDate,
                new BigDecimal("-200.00"),
                false,
                true,
                false,
                null
        );

        insertConfirmedRecurringBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                recurringTransactionId,
                logicalDate,
                new BigDecimal("-200.00"),
                false,
                true
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-200.00"),
                true,
                false,
                logicalDate,
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldRejectBucketWithFutureBaseTransaction() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket future transaction");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket future transaction");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria bucket future transaction"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-50.00"),
                true,
                false,
                today().plusDays(1),
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.futureTransactionsExist"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldRejectBucketWithOpenBaseRecurringTransaction() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket open recurring");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket open recurring");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria bucket open recurring"
        );

        insertOpenDailyRecurringTransactionUsingBucket(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                today().plusDays(100),
                new BigDecimal("-200.00"),
                false,
                true,
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.openRecurringTransactionsExist"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldIgnoreFutureSimulatedTransaction() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket simulated future");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket simulated future");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria simulated future"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.getUserGroup(),
                "Simulazione future bucket"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-200.00"),
                true,
                false,
                today().plusDays(1),
                true,
                simulationGroupId
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldIgnoreFutureTransactionLinkedToDifferentBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket other future");
        Bucket bucketToClose = createBucket(owner.getUserGroup(), "Bucket da chiudere other future");
        Bucket otherBucket = createBucket(owner.getUserGroup(), "Altro bucket future");

        linkBucketToAccount(bucketToClose, account);
        linkBucketToAccount(otherBucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria other bucket future"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                otherBucket,
                categoryId,
                new BigDecimal("-200.00"),
                true,
                false,
                today().plusDays(1),
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucketToClose))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucketToClose.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucketToClose.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldIgnoreOpenSimulatedRecurringTransaction() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto bucket open simulated recurring");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket open simulated recurring");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria open simulated recurring"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.getUserGroup(),
                "Simulazione open recurring bucket"
        );

        insertOpenDailyRecurringTransactionUsingBucket(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                today().plusDays(1),
                new BigDecimal("-200.00"),
                false,
                true,
                true,
                simulationGroupId
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldRejectRecurringTransactionWithFutureDetailsLinkedToBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto future details bucket");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket future details");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria future details bucket"
        );

        insertOpenDailyRecurringTransactionWithBucketDetailsChange(
                owner.getUserGroup(),
                account,
                categoryId,
                today().plusDays(100),
                new BigDecimal("-200.00"),
                today(),
                null,
                today().plusDays(1),
                bucket
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.openRecurringTransactionsExist"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldIgnoreRecurringTransactionLinkedToBucketOnlyInPastDetails() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto past details bucket");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket past details");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria past details bucket"
        );

        insertOpenDailyRecurringTransactionWithBucketDetailsChange(
                owner.getUserGroup(),
                account,
                categoryId,
                today().plusDays(1),
                new BigDecimal("-200.00"),
                today().minusDays(2),
                bucket,
                today(),
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldReturnBalanceErrorBeforeFutureTransactionErrorWhenBothExist() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account account = createAccount(owner.getUserGroup(), "Conto balance and future");
        Bucket bucket = createBucket(owner.getUserGroup(), "Bucket balance and future");

        linkBucketToAccount(bucket, account);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria balance and future"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-50.00"),
                false,
                true,
                today(),
                false,
                null
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                account,
                bucket,
                categoryId,
                new BigDecimal("-25.00"),
                true,
                false,
                today().plusDays(1),
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.balanceMustBeZero"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldRejectBucketWithZeroAggregatedBalanceButNonZeroAccountBalances() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account firstAccount = createAccount(owner.getUserGroup(), "Conto positivo portafoglio");
        Account secondAccount = createAccount(owner.getUserGroup(), "Conto negativo portafoglio");
        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio zero aggregato");

        linkBucketToAccount(bucket, firstAccount);
        linkBucketToAccount(bucket, secondAccount);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria zero aggregato"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                firstAccount,
                bucket,
                categoryId,
                new BigDecimal("-10000.00"),
                false,
                true,
                today(),
                false,
                null
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                secondAccount,
                bucket,
                categoryId,
                new BigDecimal("-10000.00"),
                true,
                false,
                today(),
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.accountBalancesMustBeZero"));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    @Test
    void closeBucketShouldAllowBucketWhenEveryAccountBalanceIsZero() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account firstAccount = createAccount(owner.getUserGroup(), "Conto zero per conto A");
        Account secondAccount = createAccount(owner.getUserGroup(), "Conto zero per conto B");
        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio zero per conto");

        linkBucketToAccount(bucket, firstAccount);
        linkBucketToAccount(bucket, secondAccount);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria zero per conto"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                firstAccount,
                bucket,
                categoryId,
                new BigDecimal("-10000.00"),
                false,
                true,
                today(),
                false,
                null
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                firstAccount,
                bucket,
                categoryId,
                new BigDecimal("-10000.00"),
                true,
                false,
                today(),
                false,
                null
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                secondAccount,
                bucket,
                categoryId,
                new BigDecimal("-5000.00"),
                false,
                true,
                today(),
                false,
                null
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                secondAccount,
                bucket,
                categoryId,
                new BigDecimal("-5000.00"),
                true,
                false,
                today(),
                false,
                null
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldIgnoreSimulatedAccountLevelBalances() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account firstAccount = createAccount(owner.getUserGroup(), "Conto simulato per conto A");
        Account secondAccount = createAccount(owner.getUserGroup(), "Conto simulato per conto B");
        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio simulato per conto");

        linkBucketToAccount(bucket, firstAccount);
        linkBucketToAccount(bucket, secondAccount);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria simulata per conto"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.getUserGroup(),
                "Simulazione saldo per conto"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                firstAccount,
                bucket,
                categoryId,
                new BigDecimal("-10000.00"),
                false,
                true,
                today(),
                true,
                simulationGroupId
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                secondAccount,
                bucket,
                categoryId,
                new BigDecimal("-10000.00"),
                true,
                false,
                today(),
                true,
                simulationGroupId
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.getBucketId().toString()))
                .andExpect(jsonPath("$.bucketClosedAt").exists());

        Bucket closedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(closedBucket.getBucketClosedAt()).isNotNull();
    }

    @Test
    void closeBucketShouldRejectNonZeroBalanceOnUnlinkedAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Account firstAccount = createAccount(owner.getUserGroup(), "Conto scollegato A");
        Account secondAccount = createAccount(owner.getUserGroup(), "Conto scollegato B");
        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio con conti scollegati");

        linkBucketToAccount(bucket, firstAccount);
        linkBucketToAccount(bucket, secondAccount);

        UUID categoryId = createActiveCategory(
                owner.getUserGroup(),
                owner,
                "Categoria conti scollegati"
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                firstAccount,
                bucket,
                categoryId,
                new BigDecimal("-10000.00"),
                false,
                true,
                today(),
                false,
                null
        );

        insertBucketTransaction(
                owner.getUserGroup(),
                secondAccount,
                bucket,
                categoryId,
                new BigDecimal("-10000.00"),
                true,
                false,
                today(),
                false,
                null
        );

        jdbcTemplate.update(
                """
                        delete from buckets_accounts
                        where bucket_id = ?
                          and user_group_id = ?
                        """,
                bucket.getBucketId(),
                owner.getUserGroup().getUserGroupId()
        );

        mockMvc.perform(post(bucketClosePath(bucket))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.bucket.accountBalancesMustBeZero"))
                .andExpect(jsonPath("$.path").value(bucketClosePath(bucket)));

        Bucket unchangedBucket = bucketRepository.findById(bucket.getBucketId()).orElseThrow();

        assertThat(unchangedBucket.getBucketClosedAt()).isNull();
    }

    private User createVerifiedUser(UserRole userRole) {
        return transactionTemplate.execute(status -> {
            UserGroup userGroup = new UserGroup("Test group " + UUID.randomUUID());

            User user = new User(
                    "Test user " + UUID.randomUUID(),
                    uniqueEmail("user"),
                    userGroup,
                    userRole,
                    UserPlatformRole.USER,
                    IT_LOCALE,
                    PreferredTheme.DEFAULT,
                    false,
                    true,
                    passwordEncoder.encode(DEFAULT_PASSWORD),
                    true,
                    0L
            );

            entityManager.persist(userGroup);
            entityManager.persist(user);
            entityManager.flush();

            return user;
        });
    }

    private User createVerifiedUserInGroup(UserGroup userGroup, UserRole userRole) {
        return transactionTemplate.execute(status -> {
            UserGroup managedUserGroup = entityManager.getReference(
                    UserGroup.class,
                    userGroup.getUserGroupId()
            );

            User user = new User(
                    "Test user " + UUID.randomUUID(),
                    uniqueEmail("user"),
                    managedUserGroup,
                    userRole,
                    UserPlatformRole.USER,
                    IT_LOCALE,
                    PreferredTheme.DEFAULT,
                    false,
                    true,
                    passwordEncoder.encode(DEFAULT_PASSWORD),
                    true,
                    0L
            );

            entityManager.persist(user);
            entityManager.flush();

            return user;
        });
    }

    private Account createAccount(UserGroup userGroup, String accountName) {
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        insert into accounts (
                            account_id,
                            account_name,
                            currency,
                            opening_balance,
                            opening_balance_date,
                            user_group_id
                        )
                        values (?, ?, ?, ?, ?, ?)
                        """,
                accountId,
                accountName,
                "EUR",
                new BigDecimal("0.00"),
                LocalDate.of(2026, 1, 1),
                userGroup.getUserGroupId()
        );

        return accountRepository.findById(accountId).orElseThrow();
    }

    private void grantAccountAccess(Account account, User user) {
        jdbcTemplate.update(
                """
                        insert into accounts_users (
                            account_id,
                            user_id,
                            user_group_id
                        )
                        values (?, ?, ?)
                        """,
                account.getAccountId(),
                user.getUserId(),
                user.getUserGroup().getUserGroupId()
        );
    }

    private Bucket createBucket(UserGroup userGroup, String bucketName) {
        return bucketRepository.saveAndFlush(Bucket.create(
                bucketName,
                null,
                userGroup
        ));
    }

    private void insertBucket(UserGroup userGroup, String bucketName) {
        jdbcTemplate.update(
                """
                        insert into buckets (
                            bucket_name,
                            user_group_id
                        )
                        values (?, ?)
                        """,
                bucketName,
                userGroup.getUserGroupId()
        );
    }

    private String accessTokenFor(User user) {
        return jwtTokenService.createAccessToken(user)
                .token();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private UUID bucketIdFrom(MvcResult result) throws Exception {
        String bucketId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.bucketId"
        );

        return UUID.fromString(bucketId);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private void linkBucketToAccount(Bucket bucket, Account account) {
        jdbcTemplate.update(
                """
                        insert into buckets_accounts (
                            bucket_id,
                            account_id,
                            user_group_id
                        )
                        values (?, ?, ?)
                        """,
                bucket.getBucketId(),
                account.getAccountId(),
                account.getUserGroupId()
        );
    }

    private void closeBucket(Bucket bucket) {
        jdbcTemplate.update(
                """
                        update buckets
                        set bucket_closed_at = now()
                        where bucket_id = ?
                        """,
                bucket.getBucketId()
        );
    }

    private String bucketAccountPath(Bucket bucket, Account account) {
        return BUCKETS_PATH
                + "/"
                + bucket.getBucketId()
                + "/accounts/"
                + account.getAccountId();
    }

    private String bucketClosePath(Bucket bucket) {
        return bucketClosePath(bucket.getBucketId());
    }

    private String bucketClosePath(UUID bucketId) {
        return BUCKETS_PATH
                + "/"
                + bucketId
                + "/close";
    }

    private String bucketReopenPath(Bucket bucket) {
        return bucketReopenPath(bucket.getBucketId());
    }

    private String bucketReopenPath(UUID bucketId) {
        return BUCKETS_PATH
                + "/"
                + bucketId
                + "/reopen";
    }

    private UUID createActiveCategory(
            UserGroup userGroup,
            User createdByUser,
            String categoryName
    ) {
        UUID categoryId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        insert into categories (
                            category_id,
                            user_group_id,
                            category_created_by_user_id,
                            category_current_name
                        )
                        values (?, ?, ?, ?)
                        """,
                categoryId,
                userGroup.getUserGroupId(),
                createdByUser.getUserId(),
                categoryName
        );

        jdbcTemplate.update(
                """
                        insert into category_details_history (
                            category_id,
                            category_name,
                            category_description
                        )
                        values (?, ?, ?)
                        """,
                categoryId,
                categoryName,
                "Categoria test"
        );

        jdbcTemplate.update(
                """
                        insert into category_status_history (
                            category_id,
                            category_is_active
                        )
                        values (?, true)
                        """,
                categoryId
        );

        return categoryId;
    }

    private void insertTransactionUsingBucketAccount(
            UserGroup userGroup,
            Account account,
            Bucket bucket,
            UUID categoryId
    ) {
        jdbcTemplate.update(
                """
                        insert into transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            category_id,
                            transaction_charge_date,
                            account_id,
                            bucket_id,
                            user_group_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                "Transazione con bucket account",
                new BigDecimal("-25.00"),
                categoryId,
                LocalDate.of(2026, 6, 10),
                account.getAccountId(),
                bucket.getBucketId(),
                userGroup.getUserGroupId()
        );
    }

    private void insertRecurringTransactionDetailsHistoryUsingBucketAccount(
            UserGroup userGroup,
            Account account,
            Bucket bucket,
            UUID categoryId
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        insert into recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_amount_is_adjustable,
                            recurring_transaction_first_payment_date,
                            user_group_id
                        )
                        values (?, false, ?, ?)
                        """,
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                userGroup.getUserGroupId()
        );

        jdbcTemplate.update(
                """
                        insert into recurring_transaction_details_history (
                            recurring_transaction_details_history_id,
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            linked_bucket_id,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                recurringTransactionId,
                "Ricorrente con bucket account",
                categoryId,
                financialPriorityId("ESSENTIAL"),
                account.getAccountId(),
                bucket.getBucketId(),
                LocalDate.of(2026, 6, 1),
                userGroup.getUserGroupId()
        );
    }

    private UUID financialPriorityId(String financialPriorityName) {
        return jdbcTemplate.queryForObject(
                """
                        select financial_priority_id
                        from financial_priorities
                        where financial_priority_name = ?
                        """,
                UUID.class,
                financialPriorityName
        );
    }

    private void insertBucketTransaction(
            UserGroup userGroup,
            Account account,
            Bucket bucket,
            UUID categoryId,
            BigDecimal amount,
            boolean affectsAccountBalance,
            boolean affectsSerenityline,
            LocalDate chargeDate,
            boolean simulated,
            UUID simulationGroupId
    ) {
        jdbcTemplate.update(
                """
                        insert into transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            transaction_affects_account_balance,
                            transaction_affects_serenityline,
                            category_id,
                            transaction_charge_date,
                            transaction_is_confirmed,
                            account_id,
                            bucket_id,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?, ?, true, true, 7, ?)
                        """,
                UUID.randomUUID(),
                "Movimento bucket",
                amount,
                affectsAccountBalance,
                affectsSerenityline,
                categoryId,
                chargeDate,
                account.getAccountId(),
                bucket.getBucketId(),
                simulated,
                simulationGroupId,
                userGroup.getUserGroupId()
        );
    }

    private UUID insertDailyRecurringTransactionUsingBucket(
            UserGroup userGroup,
            Account account,
            Bucket bucket,
            UUID categoryId,
            LocalDate firstPaymentDate,
            BigDecimal paymentAmount,
            boolean affectsAccountBalance,
            boolean affectsSerenityline,
            boolean simulated,
            UUID simulationGroupId
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        insert into recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_amount_is_adjustable,
                            recurring_transaction_first_payment_date,
                            recurring_transaction_is_simulated,
                            simulation_group_id,
                            recurring_transaction_reminder_enabled,
                            recurring_transaction_reminder_days_before,
                            user_group_id
                        )
                        values (?, false, ?, ?, ?, true, 7, ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                simulated,
                simulationGroupId,
                userGroup.getUserGroupId()
        );

        jdbcTemplate.update(
                """
                        insert into recurring_transaction_history (
                            recurring_transaction_id,
                            effective_from,
                            day_of_unit,
                            recurrence_interval,
                            recurrence_unit,
                            payment_date_adjustment_policy,
                            payment_amount,
                            recurring_transaction_end_date
                        )
                        values (?, ?, 1, 1, 'DAY', 'PREVIOUS_BUSINESS_DAY', ?, ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                paymentAmount,
                firstPaymentDate
        );

        jdbcTemplate.update(
                """
                        insert into recurring_transaction_details_history (
                            recurring_transaction_details_history_id,
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            linked_bucket_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_serenityline,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                recurringTransactionId,
                "Ricorrente bucket",
                categoryId,
                financialPriorityId("ESSENTIAL"),
                account.getAccountId(),
                bucket.getBucketId(),
                affectsAccountBalance,
                affectsSerenityline,
                firstPaymentDate,
                userGroup.getUserGroupId()
        );

        return recurringTransactionId;
    }

    private UUID insertOpenDailyRecurringTransactionUsingBucket(
            UserGroup userGroup,
            Account account,
            Bucket bucket,
            UUID categoryId,
            LocalDate firstPaymentDate,
            BigDecimal paymentAmount,
            boolean affectsAccountBalance,
            boolean affectsSerenityline,
            boolean simulated,
            UUID simulationGroupId
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        insert into recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_amount_is_adjustable,
                            recurring_transaction_first_payment_date,
                            recurring_transaction_is_simulated,
                            simulation_group_id,
                            recurring_transaction_reminder_enabled,
                            recurring_transaction_reminder_days_before,
                            user_group_id
                        )
                        values (?, false, ?, ?, ?, true, 7, ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                simulated,
                simulationGroupId,
                userGroup.getUserGroupId()
        );

        jdbcTemplate.update(
                """
                        insert into recurring_transaction_history (
                            recurring_transaction_id,
                            effective_from,
                            day_of_unit,
                            recurrence_interval,
                            recurrence_unit,
                            payment_date_adjustment_policy,
                            payment_amount
                        )
                        values (?, ?, 1, 1, 'DAY', 'PREVIOUS_BUSINESS_DAY', ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                paymentAmount
        );

        jdbcTemplate.update(
                """
                        insert into recurring_transaction_details_history (
                            recurring_transaction_details_history_id,
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            linked_bucket_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_serenityline,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                recurringTransactionId,
                "Ricorrente bucket aperta",
                categoryId,
                financialPriorityId("ESSENTIAL"),
                account.getAccountId(),
                bucket.getBucketId(),
                affectsAccountBalance,
                affectsSerenityline,
                firstPaymentDate,
                userGroup.getUserGroupId()
        );

        return recurringTransactionId;
    }

    private void insertConfirmedRecurringBucketTransaction(
            UserGroup userGroup,
            Account account,
            Bucket bucket,
            UUID categoryId,
            UUID recurringTransactionId,
            LocalDate logicalDate,
            BigDecimal amount,
            boolean affectsAccountBalance,
            boolean affectsSerenityline
    ) {
        jdbcTemplate.update(
                """
                        insert into transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            transaction_affects_account_balance,
                            transaction_affects_serenityline,
                            category_id,
                            transaction_charge_date,
                            transaction_is_confirmed,
                            account_id,
                            bucket_id,
                            transaction_is_simulated,
                            transaction_is_user_entered,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, true, ?, ?, false, false, ?, ?, now(), true, 7, ?)
                        """,
                UUID.randomUUID(),
                "Occorrenza ricorrente confermata bucket",
                amount,
                affectsAccountBalance,
                affectsSerenityline,
                categoryId,
                logicalDate,
                account.getAccountId(),
                bucket.getBucketId(),
                recurringTransactionId,
                logicalDate,
                userGroup.getUserGroupId()
        );
    }

    private UUID createSimulationGroup(
            UserGroup userGroup,
            String simulationGroupName
    ) {
        UUID simulationGroupId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        insert into simulation_groups (
                            simulation_group_id,
                            user_group_id,
                            simulation_group_name,
                            simulation_group_description
                        )
                        values (?, ?, ?, ?)
                        """,
                simulationGroupId,
                userGroup.getUserGroupId(),
                simulationGroupName + " " + UUID.randomUUID(),
                "Simulazione test"
        );

        return simulationGroupId;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private UUID insertOpenDailyRecurringTransactionWithBucketDetailsChange(
            UserGroup userGroup,
            Account account,
            UUID categoryId,
            LocalDate firstPaymentDate,
            BigDecimal paymentAmount,
            LocalDate firstDetailsEffectiveFrom,
            Bucket firstBucket,
            LocalDate secondDetailsEffectiveFrom,
            Bucket secondBucket
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        insert into recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_amount_is_adjustable,
                            recurring_transaction_first_payment_date,
                            recurring_transaction_is_simulated,
                            simulation_group_id,
                            recurring_transaction_reminder_enabled,
                            recurring_transaction_reminder_days_before,
                            user_group_id
                        )
                        values (?, false, ?, false, null, true, 7, ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                userGroup.getUserGroupId()
        );

        jdbcTemplate.update(
                """
                        insert into recurring_transaction_history (
                            recurring_transaction_id,
                            effective_from,
                            day_of_unit,
                            recurrence_interval,
                            recurrence_unit,
                            payment_date_adjustment_policy,
                            payment_amount
                        )
                        values (?, ?, 1, 1, 'DAY', 'PREVIOUS_BUSINESS_DAY', ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                paymentAmount
        );

        insertRecurringTransactionDetailsHistory(
                userGroup,
                account,
                categoryId,
                recurringTransactionId,
                "Ricorrente bucket storico iniziale",
                firstDetailsEffectiveFrom,
                firstBucket
        );

        insertRecurringTransactionDetailsHistory(
                userGroup,
                account,
                categoryId,
                recurringTransactionId,
                "Ricorrente bucket storico successivo",
                secondDetailsEffectiveFrom,
                secondBucket
        );

        return recurringTransactionId;
    }

    private void insertRecurringTransactionDetailsHistory(
            UserGroup userGroup,
            Account account,
            UUID categoryId,
            UUID recurringTransactionId,
            String description,
            LocalDate effectiveFrom,
            Bucket bucket
    ) {
        jdbcTemplate.update(
                """
                        insert into recurring_transaction_details_history (
                            recurring_transaction_details_history_id,
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            linked_bucket_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_serenityline,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, true, true, ?, ?)
                        """,
                UUID.randomUUID(),
                recurringTransactionId,
                description,
                categoryId,
                financialPriorityId("ESSENTIAL"),
                account.getAccountId(),
                bucket == null ? null : bucket.getBucketId(),
                effectiveFrom,
                userGroup.getUserGroupId()
        );
    }

}