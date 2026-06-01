package me.serenityline.api.export;

import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipOutputStream;

@Service
public class DataExportService {

    private static final String OWNER_ROLE = "OWNER";

    private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DataExportRepository repository;
    private final DataExportCsvWriter csvWriter;
    private final DataExportConcurrencyGuard concurrencyGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DataExportService(
            DataExportRepository repository,
            DataExportCsvWriter csvWriter,
            DataExportConcurrencyGuard concurrencyGuard,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.csvWriter = Objects.requireNonNull(csvWriter, "csvWriter");
        this.concurrencyGuard = Objects.requireNonNull(concurrencyGuard, "concurrencyGuard");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager")
        );
        this.transactionTemplate.setReadOnly(true);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DataExportFile prepareExport(AuthenticatedUser authenticatedUser) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");

        DataExportConcurrencyGuard.Permit permit =
                concurrencyGuard.acquire(authenticatedUser.userId());

        String filename = "serenityline-export-%s.zip".formatted(
                OffsetDateTime.now(clock).format(FILENAME_TIMESTAMP_FORMATTER)
        );

        StreamingResponseBody body = outputStream -> {
            try (permit) {
                writeExportInReadOnlyTransaction(
                        authenticatedUser,
                        outputStream
                );
            }
        };

        return new DataExportFile(filename, body);
    }

    private void writeExportInReadOnlyTransaction(
            AuthenticatedUser authenticatedUser,
            OutputStream outputStream
    ) throws IOException {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    writeExport(
                            authenticatedUser,
                            outputStream
                    );
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private void writeExport(
            AuthenticatedUser authenticatedUser,
            OutputStream outputStream
    ) throws IOException {
        boolean includesFinanceData = isOwner(authenticatedUser);

        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

        csvWriter.writeReadme(
                zipOutputStream,
                includesFinanceData
        );

        List<DataExportManifestEntry> manifestEntries = new ArrayList<>();

        for (DataExportTable table : personalTables()) {
            long rows = repository.exportTable(
                    table,
                    authenticatedUser,
                    zipOutputStream
            );

            manifestEntries.add(new DataExportManifestEntry(
                    table.directory(),
                    rows
            ));
        }

        if (includesFinanceData) {
            for (DataExportTable table : ownerFinanceTables()) {
                long rows = repository.exportTable(
                        table,
                        authenticatedUser,
                        zipOutputStream
                );

                manifestEntries.add(new DataExportManifestEntry(
                        table.directory(),
                        rows
                ));
            }
        }

        csvWriter.writeManifest(
                zipOutputStream,
                manifestEntries
        );

        zipOutputStream.finish();
        zipOutputStream.flush();
    }

    private boolean isOwner(AuthenticatedUser authenticatedUser) {
        return OWNER_ROLE.equals(authenticatedUser.userRole());
    }

    private List<DataExportTable> personalTables() {
        return List.of(
                new DataExportTable(
                        "user/me",
                        """
                                select
                                    user_id,
                                    user_name,
                                    email,
                                    user_group_id,
                                    user_role,
                                    user_platform_role,
                                    preferred_locale,
                                    preferred_theme,
                                    wants_invoice,
                                    user_created_at,
                                    user_updated_at,
                                    user_deleted_at,
                                    user_is_enabled,
                                    user_last_login_at,
                                    email_2fa_enabled,
                                    payment_email_reminders_enabled
                                from users
                                where user_id = ?
                                order by user_id
                                """,
                        DataExportStatementBinder.authenticatedUserId()
                ),
                new DataExportTable(
                        "user/user_sessions",
                        """
                                select
                                    user_session_id,
                                    user_id,
                                    session_created_at,
                                    session_last_seen_at,
                                    session_expires_at,
                                    session_revoked_at,
                                    session_revoke_reason,
                                    user_agent,
                                    device_label
                                from user_sessions
                                where user_id = ?
                                order by session_created_at, user_session_id
                                """,
                        DataExportStatementBinder.authenticatedUserId()
                ),
                new DataExportTable(
                        "user/login_attempts",
                        """
                                select
                                    auth_login_attempt_id,
                                    user_id,
                                    login_successful,
                                    failure_reason,
                                    auth_login_attempt_at
                                from auth_login_attempts
                                where user_id = ?
                                order by auth_login_attempt_at, auth_login_attempt_id
                                """,
                        DataExportStatementBinder.authenticatedUserId()
                ),
                new DataExportTable(
                        "user/auth_action_tokens_metadata",
                        """
                                select
                                    auth_action_token_id,
                                    user_id,
                                    auth_action_token_type,
                                    auth_action_expires_at,
                                    auth_action_used_at,
                                    auth_action_revoked_at,
                                    auth_action_created_at,
                                    auth_action_failed_attempt_count,
                                    auth_action_last_failed_at,
                                    auth_action_max_attempts,
                                    auth_action_target_value
                                from auth_action_tokens
                                where user_id = ?
                                order by auth_action_created_at, auth_action_token_id
                                """,
                        DataExportStatementBinder.authenticatedUserId()
                ),
                new DataExportTable(
                        "user/email_outbox_metadata",
                        """
                                select
                                    email_outbox_id,
                                    user_id,
                                    recipient_email,
                                    email_type,
                                    delete_body_after_send,
                                    email_body_deleted_at,
                                    provider,
                                    provider_message_id,
                                    email_status,
                                    attempts,
                                    max_attempts,
                                    email_scheduled_at,
                                    email_sent_at,
                                    email_last_failed_at,
                                    email_created_at,
                                    email_updated_at,
                                    email_cancelled_at
                                from email_outbox
                                where user_id = ?
                                order by email_created_at, email_outbox_id
                                """,
                        DataExportStatementBinder.authenticatedUserId()
                )
        );
    }

    private List<DataExportTable> ownerFinanceTables() {
        return List.of(
                new DataExportTable(
                        "owner/user_group",
                        """
                                select
                                    user_group_id,
                                    user_group_name,
                                    user_group_created_at
                                from user_groups
                                where user_group_id = ?
                                order by user_group_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "owner/group_users",
                        """
                                select
                                    user_id,
                                    user_name,
                                    email,
                                    user_group_id,
                                    user_role,
                                    user_platform_role,
                                    preferred_locale,
                                    preferred_theme,
                                    wants_invoice,
                                    user_created_at,
                                    user_updated_at,
                                    user_deleted_at,
                                    user_is_enabled,
                                    user_last_login_at,
                                    email_2fa_enabled,
                                    payment_email_reminders_enabled
                                from users
                                where user_group_id = ?
                                order by user_created_at, user_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/financial_priorities",
                        """
                                select
                                    financial_priority_id,
                                    financial_priority_name,
                                    financial_priority_description,
                                    financial_priority_ranking
                                from financial_priorities
                                order by financial_priority_ranking desc
                                """,
                        DataExportStatementBinder.none()
                ),
                new DataExportTable(
                        "finance/categories",
                        """
                                select
                                    category_id,
                                    user_group_id,
                                    category_created_by_user_id,
                                    category_current_name,
                                    category_created_at
                                from categories
                                where user_group_id = ?
                                order by category_created_at, category_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/category_status_history",
                        """
                                select
                                    csh.category_status_history_id,
                                    csh.category_id,
                                    csh.category_is_active,
                                    csh.category_status_updated_at
                                from category_status_history csh
                                join categories c on c.category_id = csh.category_id
                                where c.user_group_id = ?
                                order by csh.category_status_updated_at, csh.category_status_history_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/category_details_history",
                        """
                                select
                                    cdh.category_details_history_id,
                                    cdh.category_id,
                                    cdh.category_name,
                                    cdh.category_description,
                                    cdh.category_details_updated_at
                                from category_details_history cdh
                                join categories c on c.category_id = cdh.category_id
                                where c.user_group_id = ?
                                order by cdh.category_details_updated_at, cdh.category_details_history_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/accounts",
                        """
                                select
                                    account_id,
                                    account_name,
                                    account_description,
                                    currency,
                                    issuing_institution,
                                    account_created_at,
                                    account_updated_at,
                                    opening_balance,
                                    opening_balance_date,
                                    user_group_id
                                from accounts
                                where user_group_id = ?
                                order by account_created_at, account_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/accounts_users",
                        """
                                select
                                    account_user_id,
                                    account_id,
                                    user_id,
                                    user_group_id,
                                    account_access_granted_at
                                from accounts_users
                                where user_group_id = ?
                                order by account_access_granted_at, account_user_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/credit_cards",
                        """
                                select
                                    credit_card_id,
                                    credit_card_name,
                                    credit_card_description,
                                    credit_card_charge_day,
                                    account_id,
                                    user_group_id,
                                    credit_card_created_at,
                                    credit_card_updated_at
                                from credit_cards
                                where user_group_id = ?
                                order by credit_card_created_at, credit_card_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/buckets",
                        """
                                select
                                    bucket_id,
                                    bucket_name,
                                    bucket_description,
                                    bucket_created_at,
                                    bucket_updated_at,
                                    bucket_closed_at,
                                    user_group_id
                                from buckets
                                where user_group_id = ?
                                order by bucket_created_at, bucket_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/buckets_accounts",
                        """
                                select
                                    bucket_account_id,
                                    bucket_id,
                                    account_id,
                                    user_group_id,
                                    bucket_account_created_at
                                from buckets_accounts
                                where user_group_id = ?
                                order by bucket_account_created_at, bucket_account_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/simulation_groups",
                        """
                                select
                                    simulation_group_id,
                                    user_group_id,
                                    simulation_group_name,
                                    simulation_group_description,
                                    simulation_group_created_at,
                                    simulation_group_updated_at,
                                    simulation_group_archived_at
                                from simulation_groups
                                where user_group_id = ?
                                order by simulation_group_created_at, simulation_group_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/simulation_groups_accounts",
                        """
                                select
                                    simulation_group_account_id,
                                    simulation_group_id,
                                    account_id,
                                    user_group_id,
                                    simulation_group_account_created_at
                                from simulation_groups_accounts
                                where user_group_id = ?
                                order by simulation_group_account_created_at, simulation_group_account_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/recurring_transactions",
                        """
                                select
                                    recurring_transaction_id,
                                    recurring_transaction_amount_is_adjustable,
                                    recurring_transaction_first_payment_date,
                                    recurring_transaction_is_simulated,
                                    simulation_group_id,
                                    recurring_transaction_reminder_enabled,
                                    recurring_transaction_reminder_days_before,
                                    recurring_transaction_created_at,
                                    recurring_transaction_updated_at,
                                    user_group_id
                                from recurring_transactions
                                where user_group_id = ?
                                order by recurring_transaction_created_at, recurring_transaction_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/recurring_transaction_history",
                        """
                                select
                                    rth.recurring_transaction_history_id,
                                    rth.recurring_transaction_id,
                                    rth.effective_from,
                                    rth.effective_to,
                                    rth.day_of_unit,
                                    rth.recurrence_interval,
                                    rth.recurrence_unit,
                                    rth.payment_date_adjustment_policy,
                                    rth.payment_amount,
                                    rth.recurring_transaction_end_date,
                                    rth.final_payment_amount,
                                    rth.recurring_transaction_history_created_at
                                from recurring_transaction_history rth
                                join recurring_transactions rt
                                  on rt.recurring_transaction_id = rth.recurring_transaction_id
                                where rt.user_group_id = ?
                                order by rth.effective_from, rth.recurring_transaction_history_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/recurring_transaction_details_history",
                        """
                                select
                                    recurring_transaction_details_history_id,
                                    recurring_transaction_id,
                                    recurring_transaction_description,
                                    category_id,
                                    financial_priority_id,
                                    linked_account_id,
                                    linked_credit_card_id,
                                    linked_bucket_id,
                                    recurring_transaction_affects_account_balance,
                                    recurring_transaction_affects_serenityline,
                                    recurring_transaction_details_effective_from,
                                    recurring_transaction_details_history_created_at,
                                    user_group_id
                                from recurring_transaction_details_history
                                where user_group_id = ?
                                order by recurring_transaction_details_effective_from, recurring_transaction_details_history_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/recurring_transactions_users",
                        """
                                select
                                    recurring_transaction_user_id,
                                    recurring_transaction_id,
                                    user_id,
                                    user_group_id,
                                    recurring_transaction_user_linked_at
                                from recurring_transactions_users
                                where user_group_id = ?
                                order by recurring_transaction_user_linked_at, recurring_transaction_user_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/transactions",
                        """
                                select
                                    transaction_id,
                                    transaction_description,
                                    transaction_amount,
                                    transaction_affects_account_balance,
                                    transaction_affects_serenityline,
                                    category_id,
                                    transaction_charge_date,
                                    transaction_is_confirmed,
                                    account_id,
                                    credit_card_id,
                                    bucket_id,
                                    transaction_is_simulated,
                                    simulation_group_id,
                                    transaction_is_user_entered,
                                    recurring_transaction_id,
                                    recurring_transaction_logical_date,
                                    recurring_transaction_confirmed_at,
                                    transaction_reminder_enabled,
                                    transaction_reminder_days_before,
                                    transaction_created_at,
                                    transaction_updated_at,
                                    user_group_id
                                from transactions
                                where user_group_id = ?
                                order by transaction_charge_date, transaction_created_at, transaction_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/transactions_users",
                        """
                                select
                                    transaction_user_id,
                                    transaction_id,
                                    user_id,
                                    user_group_id,
                                    transaction_user_linked_at
                                from transactions_users
                                where user_group_id = ?
                                order by transaction_user_linked_at, transaction_user_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                ),
                new DataExportTable(
                        "finance/finance_reminder_notifications",
                        """
                                select
                                    finance_reminder_notification_id,
                                    user_id,
                                    user_group_id,
                                    transaction_id,
                                    recurring_transaction_id,
                                    recurring_transaction_logical_date,
                                    charge_date,
                                    notified_description,
                                    notified_amount,
                                    notified_currency,
                                    reminder_date,
                                    email_outbox_id,
                                    email_final_status,
                                    email_final_status_recorded_at,
                                    email_provider,
                                    provider_message_id,
                                    reminder_notification_created_at
                                from finance_reminder_notifications
                                where user_group_id = ?
                                order by reminder_notification_created_at, finance_reminder_notification_id
                                """,
                        DataExportStatementBinder.authenticatedUserGroupId()
                )
        );
    }
}