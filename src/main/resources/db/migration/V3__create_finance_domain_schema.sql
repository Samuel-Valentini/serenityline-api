ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_user_role;

ALTER TABLE users
    ADD CONSTRAINT chk_users_user_role
        CHECK (user_role IN (
                             'OWNER',
                             'SUPER_COLLABORATOR',
                             'VIEWER_COLLABORATOR',
                             'COLLABORATOR'
            ));

ALTER TABLE users
    ADD CONSTRAINT uq_users_user_id_group
        UNIQUE (user_id, user_group_id);

ALTER TABLE email_outbox
    DROP CONSTRAINT IF EXISTS chk_email_outbox_email_type;

ALTER TABLE email_outbox
    ADD CONSTRAINT chk_email_outbox_email_type
        CHECK (
            email_type IN (
                           'EMAIL_VERIFICATION',
                           'PASSWORD_RESET',
                           'EMAIL_CHANGE_CONFIRMATION',
                           'LOGIN_2FA_CODE',
                           'EMAIL_2FA_ENABLE_CONFIRMATION',
                           'EMAIL_2FA_DISABLE_CONFIRMATION',
                           'EMAIL_CHANGE_NOTIFICATION',
                           'TRANSACTION_REMINDER',
                           'RECURRING_TRANSACTION_REMINDER',
                           'GENERIC'
                )
            );

CREATE TABLE financial_priorities
(
    financial_priority_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_priority_name        VARCHAR(100) NOT NULL UNIQUE,
    financial_priority_description VARCHAR(500) NOT NULL,
    financial_priority_ranking     SMALLINT     NOT NULL UNIQUE,

    CONSTRAINT chk_financial_priorities_name
        CHECK (financial_priority_name IN (
                                           'CRITICAL',
                                           'ESSENTIAL',
                                           'OPTIONAL',
                                           'LEISURE_WELLBEING',
                                           'UNCLASSIFIED'
            )),

    CONSTRAINT chk_financial_priorities_name_not_blank
        CHECK (length(btrim(financial_priority_name)) > 0),

    CONSTRAINT chk_financial_priorities_description_not_blank
        CHECK (length(btrim(financial_priority_description)) > 0),

    CONSTRAINT chk_financial_priorities_ranking_allowed
        CHECK (financial_priority_ranking IN (80, 60, 40, 20, 0)),

    CONSTRAINT chk_financial_priorities_name_ranking_consistency
        CHECK (
            (financial_priority_name = 'CRITICAL' AND financial_priority_ranking = 80)
                OR
            (financial_priority_name = 'ESSENTIAL' AND financial_priority_ranking = 60)
                OR
            (financial_priority_name = 'OPTIONAL' AND financial_priority_ranking = 40)
                OR
            (financial_priority_name = 'LEISURE_WELLBEING' AND financial_priority_ranking = 20)
                OR
            (financial_priority_name = 'UNCLASSIFIED' AND financial_priority_ranking = 0)
            )
);

INSERT INTO financial_priorities
(financial_priority_name, financial_priority_description, financial_priority_ranking)
VALUES ('CRITICAL', 'Expenses that cannot be avoided and income that would create serious difficulty if lost.', 80),
       ('ESSENTIAL', 'Expenses or income currently considered indispensable by the user.', 60),
       ('OPTIONAL', 'Expenses or income that do not fall into the critical or essential categories.', 40),
       ('LEISURE_WELLBEING', 'Expenses or income pursued for leisure, personal wellbeing or psychophysical balance.',
        20),
       ('UNCLASSIFIED', 'Expenses or income that the user does not want to classify in the previous categories.', 0);

CREATE TABLE categories
(
    category_id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_group_id               UUID         NOT NULL REFERENCES user_groups (user_group_id) ON DELETE RESTRICT,
    category_created_by_user_id UUID         NOT NULL,
    category_current_name       VARCHAR(255) NOT NULL,
    category_created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_categories_category_group
        UNIQUE (category_id, user_group_id),

    CONSTRAINT chk_categories_current_name_not_blank
        CHECK (length(btrim(category_current_name)) > 0)
);

CREATE UNIQUE INDEX uq_categories_current_name_group
    ON categories (user_group_id, lower(btrim(category_current_name)));

CREATE INDEX idx_categories_user_group_id
    ON categories (user_group_id);

CREATE INDEX idx_categories_created_by_user_id
    ON categories (category_created_by_user_id);

CREATE TABLE category_status_history
(
    category_status_history_id UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    category_id                UUID        NOT NULL REFERENCES categories (category_id) ON DELETE CASCADE,
    category_is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    category_status_updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_category_status_history_category_id
    ON category_status_history (category_id, category_status_updated_at DESC);

CREATE TABLE category_details_history
(
    category_details_history_id UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    category_id                 UUID         NOT NULL REFERENCES categories (category_id) ON DELETE CASCADE,
    category_name               VARCHAR(255) NOT NULL,
    category_description        VARCHAR(500),
    category_details_updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_category_details_history_name_not_blank
        CHECK (length(btrim(category_name)) > 0),

    CONSTRAINT chk_category_details_history_description_not_blank
        CHECK (
            category_description IS NULL
                OR length(btrim(category_description)) > 0
            )
);

CREATE INDEX idx_category_details_history_category_id
    ON category_details_history (category_id, category_details_updated_at DESC);

CREATE TABLE accounts
(
    account_id           UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    account_name         VARCHAR(255)   NOT NULL,
    account_description  VARCHAR(1000),
    currency             VARCHAR(3)     NOT NULL,
    issuing_institution  VARCHAR(255),
    account_created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    account_updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    opening_balance      NUMERIC(19, 2) NOT NULL DEFAULT 0,
    opening_balance_date DATE           NOT NULL,
    user_group_id        UUID           NOT NULL REFERENCES user_groups (user_group_id) ON DELETE RESTRICT,

    CONSTRAINT uq_accounts_account_group
        UNIQUE (account_id, user_group_id),

    CONSTRAINT chk_accounts_name_not_blank
        CHECK (length(btrim(account_name)) > 0),

    CONSTRAINT chk_accounts_description_not_blank
        CHECK (
            account_description IS NULL
                OR length(btrim(account_description)) > 0
            ),

    CONSTRAINT chk_accounts_currency_format
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_accounts_issuing_institution_not_blank
        CHECK (
            issuing_institution IS NULL
                OR length(btrim(issuing_institution)) > 0
            ),

    CONSTRAINT chk_accounts_updated_after_created
        CHECK (account_updated_at >= account_created_at)
);

CREATE INDEX idx_accounts_user_group_id
    ON accounts (user_group_id);

CREATE INDEX idx_accounts_group_name
    ON accounts (user_group_id, account_name);

CREATE TABLE accounts_users
(
    account_user_id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    account_id                UUID        NOT NULL,
    user_id                   UUID        NOT NULL,
    user_group_id             UUID        NOT NULL,
    account_access_granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_accounts_users_account_group
        FOREIGN KEY (account_id, user_group_id)
            REFERENCES accounts (account_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_accounts_users_user_group
        FOREIGN KEY (user_id, user_group_id)
            REFERENCES users (user_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT uq_accounts_users_account_user
        UNIQUE (account_id, user_id)
);

CREATE INDEX idx_accounts_users_user_id
    ON accounts_users (user_id);

CREATE INDEX idx_accounts_users_account_id
    ON accounts_users (account_id);

CREATE INDEX idx_accounts_users_user_group_id
    ON accounts_users (user_group_id);

CREATE TABLE credit_cards
(
    credit_card_id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    credit_card_name        VARCHAR(255) NOT NULL,
    credit_card_description VARCHAR(2000),
    credit_card_charge_day  SMALLINT     NOT NULL,
    account_id              UUID         NOT NULL,
    user_group_id           UUID         NOT NULL,
    credit_card_created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    credit_card_updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_credit_cards_account_group
        FOREIGN KEY (account_id, user_group_id)
            REFERENCES accounts (account_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_credit_cards_card_group
        UNIQUE (credit_card_id, user_group_id),

    CONSTRAINT uq_credit_cards_card_account_group
        UNIQUE (credit_card_id, account_id, user_group_id),

    CONSTRAINT chk_credit_cards_name_not_blank
        CHECK (length(btrim(credit_card_name)) > 0),

    CONSTRAINT chk_credit_cards_description_not_blank
        CHECK (
            credit_card_description IS NULL
                OR length(btrim(credit_card_description)) > 0
            ),

    CONSTRAINT chk_credit_cards_charge_day_range
        CHECK (credit_card_charge_day BETWEEN 1 AND 31),

    CONSTRAINT chk_credit_cards_updated_after_created
        CHECK (credit_card_updated_at >= credit_card_created_at)
);

CREATE INDEX idx_credit_cards_account_group
    ON credit_cards (account_id, user_group_id);

CREATE INDEX idx_credit_cards_user_group_id
    ON credit_cards (user_group_id);

CREATE TABLE buckets
(
    bucket_id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    bucket_name        VARCHAR(255) NOT NULL,
    bucket_description VARCHAR(2000),
    bucket_created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    bucket_updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    bucket_closed_at   TIMESTAMPTZ,
    user_group_id      UUID         NOT NULL REFERENCES user_groups (user_group_id) ON DELETE RESTRICT,

    CONSTRAINT uq_buckets_bucket_group
        UNIQUE (bucket_id, user_group_id),

    CONSTRAINT chk_buckets_name_not_blank
        CHECK (length(btrim(bucket_name)) > 0),

    CONSTRAINT chk_buckets_description_not_blank
        CHECK (
            bucket_description IS NULL
                OR length(btrim(bucket_description)) > 0
            ),

    CONSTRAINT chk_buckets_updated_after_created
        CHECK (bucket_updated_at >= bucket_created_at),

    CONSTRAINT chk_buckets_closed_after_created
        CHECK (
            bucket_closed_at IS NULL
                OR bucket_closed_at >= bucket_created_at
            )
);

CREATE INDEX idx_buckets_user_group_id
    ON buckets (user_group_id);

CREATE INDEX idx_buckets_active_group
    ON buckets (user_group_id, bucket_name)
    WHERE bucket_closed_at IS NULL;

CREATE UNIQUE INDEX uq_buckets_active_name_group
    ON buckets (user_group_id, lower(btrim(bucket_name)))
    WHERE bucket_closed_at IS NULL;

CREATE TABLE buckets_accounts
(
    bucket_account_id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    bucket_id                 UUID        NOT NULL,
    account_id                UUID        NOT NULL,
    user_group_id             UUID        NOT NULL,
    bucket_account_created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_buckets_accounts_bucket_group
        FOREIGN KEY (bucket_id, user_group_id)
            REFERENCES buckets (bucket_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_buckets_accounts_account_group
        FOREIGN KEY (account_id, user_group_id)
            REFERENCES accounts (account_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT uq_buckets_accounts_bucket_account
        UNIQUE (bucket_id, account_id)
);

CREATE INDEX idx_buckets_accounts_account_id
    ON buckets_accounts (account_id);

CREATE INDEX idx_buckets_accounts_user_group_id
    ON buckets_accounts (user_group_id);

CREATE TABLE simulation_groups
(
    simulation_group_id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_group_id                UUID         NOT NULL REFERENCES user_groups (user_group_id) ON DELETE RESTRICT,
    simulation_group_name        VARCHAR(255) NOT NULL,
    simulation_group_description VARCHAR(2000),
    simulation_group_created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    simulation_group_updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    simulation_group_archived_at TIMESTAMPTZ,

    CONSTRAINT uq_simulation_groups_group
        UNIQUE (simulation_group_id, user_group_id),

    CONSTRAINT chk_simulation_groups_name_not_blank
        CHECK (length(btrim(simulation_group_name)) > 0),

    CONSTRAINT chk_simulation_groups_description_not_blank
        CHECK (
            simulation_group_description IS NULL
                OR length(btrim(simulation_group_description)) > 0
            ),

    CONSTRAINT chk_simulation_groups_updated_after_created
        CHECK (simulation_group_updated_at >= simulation_group_created_at),

    CONSTRAINT chk_simulation_groups_archived_after_created
        CHECK (
            simulation_group_archived_at IS NULL
                OR simulation_group_archived_at >= simulation_group_created_at
            )
);

CREATE INDEX idx_simulation_groups_user_group_id
    ON simulation_groups (user_group_id);

CREATE INDEX idx_simulation_groups_active_group
    ON simulation_groups (user_group_id, simulation_group_name)
    WHERE simulation_group_archived_at IS NULL;

CREATE UNIQUE INDEX uq_simulation_groups_active_name_group
    ON simulation_groups (user_group_id, lower(btrim(simulation_group_name)))
    WHERE simulation_group_archived_at IS NULL;

------------------

CREATE TABLE recurring_transactions
(
    recurring_transaction_id                   UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    recurring_transaction_amount_is_adjustable BOOLEAN     NOT NULL DEFAULT TRUE,
    recurring_transaction_first_payment_date   DATE        NOT NULL,
    recurring_transaction_is_simulated         BOOLEAN     NOT NULL DEFAULT FALSE,
    simulation_group_id                        UUID,
    recurring_transaction_reminder_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    recurring_transaction_reminder_days_before SMALLINT    NOT NULL DEFAULT 7,
    recurring_transaction_created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    recurring_transaction_updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_group_id                              UUID        NOT NULL REFERENCES user_groups (user_group_id) ON DELETE RESTRICT,

    CONSTRAINT uq_recurring_transactions_transaction_group
        UNIQUE (recurring_transaction_id, user_group_id),

    CONSTRAINT fk_recurring_transactions_simulation_group
        FOREIGN KEY (simulation_group_id, user_group_id)
            REFERENCES simulation_groups (simulation_group_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT chk_recurring_transactions_simulation_consistency
        CHECK (
            (
                recurring_transaction_is_simulated = TRUE
                    AND simulation_group_id IS NOT NULL
                )
                OR
            (
                recurring_transaction_is_simulated = FALSE
                    AND simulation_group_id IS NULL
                )
            ),

    CONSTRAINT chk_recurring_transactions_reminder_days_range
        CHECK (recurring_transaction_reminder_days_before BETWEEN 0 AND 366),

    CONSTRAINT chk_recurring_transactions_updated_after_created
        CHECK (recurring_transaction_updated_at >= recurring_transaction_created_at)
);

CREATE INDEX idx_recurring_transactions_user_group_id
    ON recurring_transactions (user_group_id);

CREATE INDEX idx_recurring_transactions_simulation_group_id
    ON recurring_transactions (simulation_group_id)
    WHERE simulation_group_id IS NOT NULL;

CREATE INDEX idx_recurring_transactions_first_payment_date
    ON recurring_transactions (user_group_id, recurring_transaction_first_payment_date);

CREATE TABLE recurring_transaction_history
(
    recurring_transaction_history_id         UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    recurring_transaction_id                 UUID           NOT NULL REFERENCES recurring_transactions (recurring_transaction_id) ON DELETE CASCADE,
    effective_from                           DATE           NOT NULL,
    effective_to                             DATE,
    day_of_unit                              SMALLINT       NOT NULL,
    recurrence_interval                      SMALLINT       NOT NULL,
    recurrence_unit                          VARCHAR(50)    NOT NULL,
    payment_date_adjustment_policy           VARCHAR(50)    NOT NULL DEFAULT 'NONE',
    payment_amount                           NUMERIC(19, 2) NOT NULL,
    recurring_transaction_end_date           DATE,
    final_payment_amount                     NUMERIC(19, 2),
    recurring_transaction_history_created_at TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT chk_recurring_transaction_history_effective_dates
        CHECK (
            effective_to IS NULL
                OR effective_to > effective_from
            ),

    CONSTRAINT chk_recurring_transaction_history_recurrence_interval
        CHECK (recurrence_interval > 0),

    CONSTRAINT chk_recurring_transaction_history_recurrence_unit
        CHECK (recurrence_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),

    CONSTRAINT chk_recurring_transaction_history_day_of_unit
        CHECK (
            (
                recurrence_unit = 'DAY'
                    AND day_of_unit = 1
                )
                OR
            (
                recurrence_unit = 'WEEK'
                    AND day_of_unit BETWEEN 1 AND 7
                )
                OR
            (
                recurrence_unit = 'MONTH'
                    AND day_of_unit BETWEEN 1 AND 31
                )
                OR
            (
                recurrence_unit = 'YEAR'
                    AND day_of_unit BETWEEN 1 AND 366
                )
            ),

    CONSTRAINT chk_recurring_transaction_history_adjustment_policy
        CHECK (payment_date_adjustment_policy IN (
                                                  'NONE',
                                                  'PREVIOUS_BUSINESS_DAY',
                                                  'NEXT_BUSINESS_DAY'
            )),

    CONSTRAINT chk_recurring_transaction_history_payment_amount_not_zero
        CHECK (payment_amount <> 0),

    CONSTRAINT chk_recurring_transaction_history_final_amount_not_zero
        CHECK (
            final_payment_amount IS NULL
                OR final_payment_amount <> 0
            ),

    CONSTRAINT chk_recurring_transaction_history_end_date
        CHECK (
            recurring_transaction_end_date IS NULL
                OR recurring_transaction_end_date >= effective_from
            ),

    CONSTRAINT chk_recurring_transaction_history_final_amount_requires_end
        CHECK (
            final_payment_amount IS NULL
                OR recurring_transaction_end_date IS NOT NULL
            )
);

CREATE INDEX idx_recurring_transaction_history_lookup
    ON recurring_transaction_history (
                                      recurring_transaction_id,
                                      effective_from DESC,
                                      recurring_transaction_history_created_at DESC
        );

CREATE TABLE recurring_transaction_details_history
(
    recurring_transaction_details_history_id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    recurring_transaction_id                         UUID         NOT NULL,
    recurring_transaction_description                VARCHAR(500) NOT NULL,
    category_id                                      UUID         NOT NULL,
    financial_priority_id                            UUID         NOT NULL REFERENCES financial_priorities (financial_priority_id) ON DELETE RESTRICT,
    linked_account_id                                UUID         NOT NULL,
    linked_credit_card_id                            UUID,
    linked_bucket_id                                 UUID,
    recurring_transaction_affects_account_balance    BOOLEAN      NOT NULL DEFAULT TRUE,
    recurring_transaction_affects_liquidity          BOOLEAN      NOT NULL DEFAULT TRUE,
    recurring_transaction_details_effective_from     DATE         NOT NULL DEFAULT CURRENT_DATE,
    recurring_transaction_details_history_created_at TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    user_group_id                                    UUID         NOT NULL,

    CONSTRAINT fk_recurring_transaction_details_recurring_group
        FOREIGN KEY (recurring_transaction_id, user_group_id)
            REFERENCES recurring_transactions (recurring_transaction_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_recurring_transaction_details_category_group
        FOREIGN KEY (category_id, user_group_id)
            REFERENCES categories (category_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_recurring_transaction_details_account_group
        FOREIGN KEY (linked_account_id, user_group_id)
            REFERENCES accounts (account_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_recurring_transaction_details_credit_card_account_group
        FOREIGN KEY (linked_credit_card_id, linked_account_id, user_group_id)
            REFERENCES credit_cards (credit_card_id, account_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_recurring_transaction_details_bucket_group
        FOREIGN KEY (linked_bucket_id, user_group_id)
            REFERENCES buckets (bucket_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_recurring_transaction_details_description_not_blank
        CHECK (length(btrim(recurring_transaction_description)) > 0),

    CONSTRAINT chk_recurring_transaction_details_affects_something
        CHECK (
            recurring_transaction_affects_account_balance = TRUE
                OR recurring_transaction_affects_liquidity = TRUE
            )
);

CREATE INDEX idx_recurring_transaction_details_history_lookup
    ON recurring_transaction_details_history (
                                              recurring_transaction_id,
                                              recurring_transaction_details_effective_from DESC,
                                              recurring_transaction_details_history_created_at DESC
        );

CREATE INDEX idx_recurring_transaction_details_history_category_id
    ON recurring_transaction_details_history (category_id);

CREATE INDEX idx_recurring_transaction_details_history_account_id
    ON recurring_transaction_details_history (linked_account_id);

CREATE INDEX idx_recurring_transaction_details_history_credit_card_id
    ON recurring_transaction_details_history (linked_credit_card_id)
    WHERE linked_credit_card_id IS NOT NULL;

CREATE INDEX idx_recurring_transaction_details_history_bucket_id
    ON recurring_transaction_details_history (linked_bucket_id)
    WHERE linked_bucket_id IS NOT NULL;

CREATE INDEX idx_recurring_transaction_details_history_user_group_id
    ON recurring_transaction_details_history (user_group_id);

CREATE TABLE recurring_transactions_users
(
    recurring_transaction_user_id        UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    recurring_transaction_id             UUID        NOT NULL,
    user_id                              UUID        NOT NULL,
    user_group_id                        UUID        NOT NULL,
    recurring_transaction_user_linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_recurring_transactions_users_transaction_group
        FOREIGN KEY (recurring_transaction_id, user_group_id)
            REFERENCES recurring_transactions (recurring_transaction_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_recurring_transactions_users_user_group
        FOREIGN KEY (user_id, user_group_id)
            REFERENCES users (user_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT uq_recurring_transactions_users_transaction_user
        UNIQUE (recurring_transaction_id, user_id)
);

CREATE INDEX idx_recurring_transactions_users_user_group
    ON recurring_transactions_users (user_id, user_group_id);

CREATE INDEX idx_recurring_transactions_users_transaction_group
    ON recurring_transactions_users (recurring_transaction_id, user_group_id);

CREATE INDEX idx_recurring_transactions_users_user_group_id
    ON recurring_transactions_users (user_group_id);

CREATE TABLE transactions
(
    transaction_id                      UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    transaction_description             VARCHAR(500)   NOT NULL,
    transaction_amount                  NUMERIC(19, 2) NOT NULL,
    transaction_affects_account_balance BOOLEAN        NOT NULL DEFAULT TRUE,
    transaction_affects_liquidity       BOOLEAN        NOT NULL DEFAULT TRUE,
    category_id                         UUID           NOT NULL,
    transaction_charge_date             DATE           NOT NULL,
    transaction_is_confirmed            BOOLEAN        NOT NULL DEFAULT FALSE,
    account_id                          UUID           NOT NULL,
    credit_card_id                      UUID,
    bucket_id                           UUID,
    transaction_is_simulated            BOOLEAN        NOT NULL DEFAULT FALSE,
    simulation_group_id                 UUID,
    transaction_is_user_entered         BOOLEAN        NOT NULL DEFAULT TRUE,
    recurring_transaction_id            UUID,
    transaction_reminder_enabled        BOOLEAN        NOT NULL DEFAULT TRUE,
    transaction_reminder_days_before    SMALLINT       NOT NULL DEFAULT 7,
    transaction_created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    transaction_updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    user_group_id                       UUID           NOT NULL REFERENCES user_groups (user_group_id) ON DELETE RESTRICT,

    CONSTRAINT uq_transactions_transaction_group
        UNIQUE (transaction_id, user_group_id),

    CONSTRAINT fk_transactions_category_group
        FOREIGN KEY (category_id, user_group_id)
            REFERENCES categories (category_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_transactions_account_group
        FOREIGN KEY (account_id, user_group_id)
            REFERENCES accounts (account_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_transactions_credit_card_account_group
        FOREIGN KEY (credit_card_id, account_id, user_group_id)
            REFERENCES credit_cards (credit_card_id, account_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_transactions_bucket_group
        FOREIGN KEY (bucket_id, user_group_id)
            REFERENCES buckets (bucket_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_transactions_simulation_group
        FOREIGN KEY (simulation_group_id, user_group_id)
            REFERENCES simulation_groups (simulation_group_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_transactions_recurring_transaction_group
        FOREIGN KEY (recurring_transaction_id, user_group_id)
            REFERENCES recurring_transactions (recurring_transaction_id, user_group_id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_transactions_description_not_blank
        CHECK (length(btrim(transaction_description)) > 0),

    CONSTRAINT chk_transactions_amount_not_zero
        CHECK (transaction_amount <> 0),

    CONSTRAINT chk_transactions_affects_something
        CHECK (
            transaction_affects_account_balance = TRUE
                OR transaction_affects_liquidity = TRUE
            ),

    CONSTRAINT chk_transactions_simulation_consistency
        CHECK (
            (
                transaction_is_simulated = TRUE
                    AND simulation_group_id IS NOT NULL
                )
                OR
            (
                transaction_is_simulated = FALSE
                    AND simulation_group_id IS NULL
                )
            ),

    CONSTRAINT chk_transactions_recurring_consistency
        CHECK (
            (
                transaction_is_user_entered = TRUE
                    AND recurring_transaction_id IS NULL
                )
                OR
            (
                transaction_is_user_entered = FALSE
                    AND recurring_transaction_id IS NOT NULL
                    AND transaction_is_confirmed = TRUE
                )
            ),

    CONSTRAINT chk_transactions_reminder_days_range
        CHECK (transaction_reminder_days_before BETWEEN 0 AND 366),

    CONSTRAINT chk_transactions_updated_after_created
        CHECK (transaction_updated_at >= transaction_created_at)
);

CREATE INDEX idx_transactions_group_charge_date
    ON transactions (user_group_id, transaction_charge_date);

CREATE INDEX idx_transactions_account_charge_date
    ON transactions (account_id, transaction_charge_date);

CREATE INDEX idx_transactions_category_id
    ON transactions (category_id);

CREATE INDEX idx_transactions_credit_card_id
    ON transactions (credit_card_id)
    WHERE credit_card_id IS NOT NULL;

CREATE INDEX idx_transactions_bucket_id
    ON transactions (bucket_id)
    WHERE bucket_id IS NOT NULL;

CREATE INDEX idx_transactions_simulation_group_id
    ON transactions (simulation_group_id)
    WHERE simulation_group_id IS NOT NULL;

CREATE INDEX idx_transactions_recurring_transaction_id
    ON transactions (recurring_transaction_id)
    WHERE recurring_transaction_id IS NOT NULL;

CREATE UNIQUE INDEX uq_transactions_recurring_occurrence
    ON transactions (recurring_transaction_id, transaction_charge_date)
    WHERE recurring_transaction_id IS NOT NULL
        AND transaction_is_user_entered = FALSE;

CREATE TABLE transactions_users
(
    transaction_user_id        UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    transaction_id             UUID        NOT NULL,
    user_id                    UUID        NOT NULL,
    user_group_id              UUID        NOT NULL,
    transaction_user_linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_transactions_users_transaction_group
        FOREIGN KEY (transaction_id, user_group_id)
            REFERENCES transactions (transaction_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_transactions_users_user_group
        FOREIGN KEY (user_id, user_group_id)
            REFERENCES users (user_id, user_group_id)
            ON DELETE CASCADE,

    CONSTRAINT uq_transactions_users_transaction_user
        UNIQUE (transaction_id, user_id)
);

CREATE INDEX idx_transactions_users_user_id
    ON transactions_users (user_id);

CREATE INDEX idx_transactions_users_transaction_id
    ON transactions_users (transaction_id);

CREATE INDEX idx_transactions_users_user_group_id
    ON transactions_users (user_group_id);
