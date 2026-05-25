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
    void findBucketShouldReturnNotFoundForClosedBucket() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        Bucket bucket = createBucket(owner.getUserGroup(), "Portafoglio chiuso");

        closeBucket(bucket);

        mockMvc.perform(get(BUCKETS_PATH + "/" + bucket.getBucketId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"))
                .andExpect(jsonPath("$.path").value(BUCKETS_PATH + "/" + bucket.getBucketId()));
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
}