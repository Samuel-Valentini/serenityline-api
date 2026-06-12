ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_credit_card_bucket_mutually_exclusive
        CHECK (
            credit_card_id IS NULL
                OR bucket_id IS NULL
            );

ALTER TABLE recurring_transaction_details_history
    ADD CONSTRAINT chk_recurring_details_credit_card_bucket_mutually_exclusive
        CHECK (
            linked_credit_card_id IS NULL
                OR linked_bucket_id IS NULL
            );