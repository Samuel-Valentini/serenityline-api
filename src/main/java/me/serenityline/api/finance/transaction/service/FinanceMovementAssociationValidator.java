package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import org.springframework.stereotype.Component;

@Component
public class FinanceMovementAssociationValidator {

    public void validateTransactionCreditCardBucketExclusion(
            CreditCard creditCard,
            Bucket bucket
    ) {
        if (creditCard != null && bucket != null) {
            throw new IllegalArgumentException("finance.transaction.creditCardAndBucketMutuallyExclusive");
        }
    }

    public void validateRecurringCreditCardBucketExclusion(
            CreditCard linkedCreditCard,
            Bucket linkedBucket
    ) {
        if (linkedCreditCard != null && linkedBucket != null) {
            throw new IllegalArgumentException("finance.recurringTransaction.creditCardAndBucketMutuallyExclusive");
        }
    }
}