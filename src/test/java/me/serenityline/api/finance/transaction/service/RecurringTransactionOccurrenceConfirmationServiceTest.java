package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionOccurrenceConfirmRequest;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.entity.TransactionUser;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.finance.transaction.repository.TransactionUserRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionOccurrenceConfirmationServiceTest {

    private static final UUID CURRENT_USER_ID = UUID.randomUUID();
    private static final UUID USER_GROUP_ID = UUID.randomUUID();
    private static final UUID RECURRING_TRANSACTION_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private static final LocalDate FIRST_PAYMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate LOGICAL_DATE = LocalDate.of(2026, 6, 1);
    private static final LocalDate CHARGE_DATE = LocalDate.of(2026, 6, 1);

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionUserRepository transactionUserRepository;

    @Mock
    private RecurringTransactionAccessService recurringTransactionAccessService;

    @Mock
    private TransactionAccessService transactionAccessService;

    @Mock
    private RecurringTransactionProjectedMovementBatchService recurringTransactionProjectedMovementBatchService;

    private RecurringTransactionOccurrenceConfirmationService service;

    private static Stream<DataIntegrityViolationException> unrelatedIntegrityViolations() {
        return Stream.of(
                new DataIntegrityViolationException(
                        "Primary key violation",
                        new ConstraintViolationException(
                                "Duplicate primary key",
                                new SQLException("Unique constraint violation", "23505"),
                                "transactions_pkey"
                        )
                ),
                new DataIntegrityViolationException(
                        "Foreign key violation",
                        new ConstraintViolationException(
                                "Invalid account reference",
                                new SQLException("Foreign key violation", "23503"),
                                "fk_transactions_account_group"
                        )
                ),
                new DataIntegrityViolationException(
                        "Unknown unique constraint",
                        new ConstraintViolationException(
                                "Unique constraint violation",
                                new SQLException("Unique constraint violation", "23505"),
                                null
                        )
                ),
                new DataIntegrityViolationException(
                        "uq_transactions_recurring_logical_occurrence"
                )
        );
    }

    @BeforeEach
    void setUp() {
        service = new RecurringTransactionOccurrenceConfirmationService(
                userRepository,
                transactionRepository,
                transactionUserRepository,
                recurringTransactionAccessService,
                transactionAccessService,
                recurringTransactionProjectedMovementBatchService
        );
    }

    @Test
    void confirmOccurrenceShouldCreateConfirmedRecurringTransactionUsingProjectedValues() {
        UserFixture userFixture = givenCurrentUser();
        RecurringTransaction recurringTransaction = givenOperableRecurringTransaction(userFixture, true);
        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(userFixture.userGroup());

        when(transactionRepository.existsConfirmedRecurringOccurrence(
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE
        )).thenReturn(false);

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementForLogicalDate(
                eq(seed()),
                eq(LOGICAL_DATE)
        )).thenReturn(Optional.of(projectedMovement));

        when(transactionAccessService.findOperableAccount(
                userFixture.user(),
                USER_GROUP_ID,
                ACCOUNT_ID
        )).thenReturn(projectedMovement.linkedAccount());

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        LOGICAL_DATE,
                        null,
                        null
                );

        TransactionResponse response = service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        );

        assertThat(response.transactionDescription()).isEqualTo("Affitto previsto");
        assertThat(response.transactionAmount()).isEqualByComparingTo("-100.00");
        assertThat(response.transactionChargeDate()).isEqualTo(CHARGE_DATE);
        assertThat(response.transactionIsConfirmed()).isTrue();
        assertThat(response.transactionIsUserEntered()).isFalse();
        assertThat(response.recurringTransactionId()).isEqualTo(RECURRING_TRANSACTION_ID);
        assertThat(response.recurringTransactionLogicalDate()).isEqualTo(LOGICAL_DATE);
        assertThat(response.recurringTransactionConfirmedAt()).isNotNull();
        assertThat(response.transactionReminderEnabled()).isTrue();
        assertThat(response.transactionReminderDaysBefore()).isEqualTo((short) 7);

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());

        Transaction savedTransaction = transactionCaptor.getValue();

        assertThat(savedTransaction.isTransactionIsUserEntered()).isFalse();
        assertThat(savedTransaction.isTransactionIsConfirmed()).isTrue();
        assertThat(savedTransaction.getRecurringTransaction()).isSameAs(recurringTransaction);
        assertThat(savedTransaction.getRecurringTransactionLogicalDate()).isEqualTo(LOGICAL_DATE);
        assertThat(savedTransaction.getRecurringTransactionConfirmedAt()).isNotNull();
        assertThat(savedTransaction.getTransactionAmount()).isEqualByComparingTo("-100.00");
        assertThat(savedTransaction.getTransactionChargeDate()).isEqualTo(CHARGE_DATE);
        assertThat(savedTransaction.getAccount().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedTransaction.getCategory().getCategoryId()).isEqualTo(CATEGORY_ID);

        verify(transactionUserRepository).save(any(TransactionUser.class));
    }

    @Test
    void confirmOccurrenceShouldUseAmountAndChargeDateOverridesWhenAmountIsAdjustable() {
        UserFixture userFixture = givenCurrentUser();
        givenOperableRecurringTransaction(userFixture, true);

        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(userFixture.userGroup());
        LocalDate actualChargeDate = LocalDate.of(2026, 6, 3);

        when(transactionRepository.existsConfirmedRecurringOccurrence(
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE
        )).thenReturn(false);

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementForLogicalDate(
                eq(seed()),
                eq(LOGICAL_DATE)
        )).thenReturn(Optional.of(projectedMovement));

        when(transactionAccessService.findOperableAccount(
                userFixture.user(),
                USER_GROUP_ID,
                ACCOUNT_ID
        )).thenReturn(projectedMovement.linkedAccount());

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        LOGICAL_DATE,
                        new BigDecimal("-95.50"),
                        actualChargeDate
                );

        TransactionResponse response = service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        );

        assertThat(response.transactionAmount()).isEqualByComparingTo("-95.50");
        assertThat(response.transactionChargeDate()).isEqualTo(actualChargeDate);

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());

        assertThat(transactionCaptor.getValue().getTransactionAmount())
                .isEqualByComparingTo("-95.50");
        assertThat(transactionCaptor.getValue().getTransactionChargeDate())
                .isEqualTo(actualChargeDate);
    }

    @Test
    void confirmOccurrenceShouldRejectMissingLogicalDate() {
        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        null,
                        null,
                        null
                );

        assertThatThrownBy(() -> service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.occurrenceLogicalDateRequired");

        verifyNoInteractions(
                userRepository,
                recurringTransactionAccessService,
                transactionRepository,
                recurringTransactionProjectedMovementBatchService,
                transactionAccessService,
                transactionUserRepository
        );
    }

    @Test
    void confirmOccurrenceShouldRejectAlreadyConfirmedOccurrence() {
        UserFixture userFixture = givenCurrentUser();
        givenOperableRecurringTransaction(userFixture, true);

        when(transactionRepository.existsConfirmedRecurringOccurrence(
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE
        )).thenReturn(true);

        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        LOGICAL_DATE,
                        null,
                        null
                );

        assertThatThrownBy(() -> service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.occurrenceAlreadyConfirmed");

        verifyNoInteractions(
                recurringTransactionProjectedMovementBatchService,
                transactionAccessService,
                transactionUserRepository
        );

        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmOccurrenceShouldRejectMissingProjectedOccurrence() {
        UserFixture userFixture = givenCurrentUser();
        givenOperableRecurringTransaction(userFixture, true);

        when(transactionRepository.existsConfirmedRecurringOccurrence(
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE
        )).thenReturn(false);

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementForLogicalDate(
                eq(seed()),
                eq(LOGICAL_DATE)
        )).thenReturn(Optional.empty());

        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        LOGICAL_DATE,
                        null,
                        null
                );

        assertThatThrownBy(() -> service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("finance.recurringTransaction.occurrenceNotFound");

        verifyNoInteractions(transactionAccessService, transactionUserRepository);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmOccurrenceShouldRejectAmountOverrideWhenRecurringAmountIsNotAdjustable() {
        UserFixture userFixture = givenCurrentUser();
        givenOperableRecurringTransaction(userFixture, false);

        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(userFixture.userGroup());

        when(transactionRepository.existsConfirmedRecurringOccurrence(
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE
        )).thenReturn(false);

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementForLogicalDate(
                eq(seed()),
                eq(LOGICAL_DATE)
        )).thenReturn(Optional.of(projectedMovement));

        when(transactionAccessService.findOperableAccount(
                userFixture.user(),
                USER_GROUP_ID,
                ACCOUNT_ID
        )).thenReturn(projectedMovement.linkedAccount());

        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        LOGICAL_DATE,
                        new BigDecimal("-90.00"),
                        null
                );

        assertThatThrownBy(() -> service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.amountNotAdjustable");

        verify(transactionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(transactionUserRepository);
    }

    @Test
    void confirmOccurrenceShouldRejectAmountWithInvalidScale() {
        UserFixture userFixture = givenCurrentUser();
        givenOperableRecurringTransaction(userFixture, true);

        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(userFixture.userGroup());

        when(transactionRepository.existsConfirmedRecurringOccurrence(
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE
        )).thenReturn(false);

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementForLogicalDate(
                eq(seed()),
                eq(LOGICAL_DATE)
        )).thenReturn(Optional.of(projectedMovement));

        when(transactionAccessService.findOperableAccount(
                userFixture.user(),
                USER_GROUP_ID,
                ACCOUNT_ID
        )).thenReturn(projectedMovement.linkedAccount());

        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        LOGICAL_DATE,
                        new BigDecimal("-90.001"),
                        null
                );

        assertThatThrownBy(() -> service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.transaction.amountInvalid");

        verify(transactionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(transactionUserRepository);
    }

    @Test
    void confirmOccurrenceShouldRejectWhenUserCannotOperateProjectedAccount() {
        UserFixture userFixture = givenCurrentUser();
        givenOperableRecurringTransaction(userFixture, true);

        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(userFixture.userGroup());

        when(transactionRepository.existsConfirmedRecurringOccurrence(
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE
        )).thenReturn(false);

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementForLogicalDate(
                eq(seed()),
                eq(LOGICAL_DATE)
        )).thenReturn(Optional.of(projectedMovement));

        when(transactionAccessService.findOperableAccount(
                userFixture.user(),
                USER_GROUP_ID,
                ACCOUNT_ID
        )).thenThrow(new AccessDeniedException("finance.account.operationNotAllowed"));

        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        LOGICAL_DATE,
                        null,
                        null
                );

        assertThatThrownBy(() -> service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("finance.account.operationNotAllowed");

        verify(transactionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(transactionUserRepository);
    }

    @Test
    void confirmOccurrenceShouldTranslateDuplicateRaceToAlreadyConfirmedError() {
        UserFixture userFixture = givenCurrentUser();
        givenOperableRecurringTransaction(userFixture, true);

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement(userFixture.userGroup());

        when(transactionRepository.existsConfirmedRecurringOccurrence(
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE
        )).thenReturn(false);

        when(recurringTransactionProjectedMovementBatchService
                .generateProjectedMovementForLogicalDate(
                        eq(seed()),
                        eq(LOGICAL_DATE)
                ))
                .thenReturn(Optional.of(projectedMovement));

        when(transactionAccessService.findOperableAccount(
                userFixture.user(),
                USER_GROUP_ID,
                ACCOUNT_ID
        )).thenReturn(projectedMovement.linkedAccount());

        ConstraintViolationException constraintViolation =
                new ConstraintViolationException(
                        "Duplicate recurring occurrence",
                        new SQLException("Unique constraint violation", "23505"),
                        "uq_transactions_recurring_logical_occurrence"
                );

        DataIntegrityViolationException duplicateException =
                new DataIntegrityViolationException(
                        "Could not execute statement",
                        constraintViolation
                );

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenThrow(duplicateException);

        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        LOGICAL_DATE,
                        null,
                        null
                );

        assertThatThrownBy(() -> service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.occurrenceAlreadyConfirmed")
                .hasCause(duplicateException);

        // Solo il controllo iniziale: nessuna nuova query dopo il fallimento.
        verify(transactionRepository, times(1))
                .existsConfirmedRecurringOccurrence(
                        USER_GROUP_ID,
                        RECURRING_TRANSACTION_ID,
                        LOGICAL_DATE
                );

        verifyNoInteractions(transactionUserRepository);
    }

    @ParameterizedTest
    @MethodSource("unrelatedIntegrityViolations")
    void confirmOccurrenceShouldPropagateUnrelatedIntegrityViolations(
            DataIntegrityViolationException exception
    ) {
        UserFixture userFixture = givenCurrentUser();
        givenOperableRecurringTransaction(userFixture, true);

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement(userFixture.userGroup());

        when(transactionRepository.existsConfirmedRecurringOccurrence(
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE
        )).thenReturn(false);

        when(recurringTransactionProjectedMovementBatchService
                .generateProjectedMovementForLogicalDate(
                        eq(seed()),
                        eq(LOGICAL_DATE)
                ))
                .thenReturn(Optional.of(projectedMovement));

        when(transactionAccessService.findOperableAccount(
                userFixture.user(),
                USER_GROUP_ID,
                ACCOUNT_ID
        )).thenReturn(projectedMovement.linkedAccount());

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenThrow(exception);

        RecurringTransactionOccurrenceConfirmRequest request =
                new RecurringTransactionOccurrenceConfirmRequest(
                        LOGICAL_DATE,
                        null,
                        null
                );

        assertThatThrownBy(() -> service.confirmOccurrence(
                CURRENT_USER_ID,
                RECURRING_TRANSACTION_ID,
                request
        ))
                .isSameAs(exception);

        verify(transactionRepository, times(1))
                .existsConfirmedRecurringOccurrence(
                        USER_GROUP_ID,
                        RECURRING_TRANSACTION_ID,
                        LOGICAL_DATE
                );

        verifyNoInteractions(transactionUserRepository);
    }

    private UserFixture givenCurrentUser() {
        UserGroup userGroup = mock(UserGroup.class);
        lenient().when(userGroup.getUserGroupId())
                .thenReturn(USER_GROUP_ID);

        User user = mock(User.class);
        lenient().when(user.getUserId())
                .thenReturn(CURRENT_USER_ID);
        lenient().when(user.getUserGroup())
                .thenReturn(userGroup);

        when(userRepository.findById(CURRENT_USER_ID))
                .thenReturn(Optional.of(user));

        return new UserFixture(user, userGroup);
    }

    private RecurringTransaction givenOperableRecurringTransaction(
            UserFixture userFixture,
            boolean amountAdjustable
    ) {
        RecurringTransaction recurringTransaction = recurringTransaction(
                userFixture.userGroup(),
                amountAdjustable
        );

        when(recurringTransactionAccessService.findOperableRecurringTransaction(
                userFixture.user(),
                USER_GROUP_ID,
                RECURRING_TRANSACTION_ID
        )).thenReturn(recurringTransaction);

        return recurringTransaction;
    }

    private RecurringTransaction recurringTransaction(
            UserGroup userGroup,
            boolean amountAdjustable
    ) {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);

        lenient().when(recurringTransaction.getRecurringTransactionId())
                .thenReturn(RECURRING_TRANSACTION_ID);
        lenient().when(recurringTransaction.getRecurringTransactionFirstPaymentDate())
                .thenReturn(FIRST_PAYMENT_DATE);
        lenient().when(recurringTransaction.getUserGroup())
                .thenReturn(userGroup);
        lenient().when(recurringTransaction.isRecurringTransactionAmountIsAdjustable())
                .thenReturn(amountAdjustable);
        lenient().when(recurringTransaction.isRecurringTransactionIsSimulated())
                .thenReturn(false);
        lenient().when(recurringTransaction.getSimulationGroup())
                .thenReturn(null);
        lenient().when(recurringTransaction.isRecurringTransactionReminderEnabled())
                .thenReturn(true);
        lenient().when(recurringTransaction.getRecurringTransactionReminderDaysBefore())
                .thenReturn((short) 7);

        return recurringTransaction;
    }

    private RecurringTransactionProjectedMovement projectedMovement(UserGroup userGroup) {
        Account account = mock(Account.class);
        lenient().when(account.getAccountId())
                .thenReturn(ACCOUNT_ID);
        lenient().when(account.getUserGroup())
                .thenReturn(userGroup);

        Category category = mock(Category.class);
        lenient().when(category.getCategoryId())
                .thenReturn(CATEGORY_ID);
        lenient().when(category.getUserGroup())
                .thenReturn(userGroup);

        return new RecurringTransactionProjectedMovement(
                RECURRING_TRANSACTION_ID,
                LOGICAL_DATE,
                CHARGE_DATE,
                new BigDecimal("-100.00"),
                false,
                "Affitto previsto",
                category,
                mock(FinancialPriority.class),
                account,
                null,
                null,
                true,
                true
        );
    }

    private RecurringTransactionProjectedMovementSeed seed() {
        return new RecurringTransactionProjectedMovementSeed(
                RECURRING_TRANSACTION_ID,
                USER_GROUP_ID,
                FIRST_PAYMENT_DATE
        );
    }

    private record UserFixture(
            User user,
            UserGroup userGroup
    ) {
    }
}