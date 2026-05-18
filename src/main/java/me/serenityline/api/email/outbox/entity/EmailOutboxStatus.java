package me.serenityline.api.email.outbox.entity;

public enum EmailOutboxStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELLED
}
