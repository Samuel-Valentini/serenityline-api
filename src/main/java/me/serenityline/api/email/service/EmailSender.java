package me.serenityline.api.email.service;

public interface EmailSender {

    String provider();

    SentEmail send(OutboundEmail email);
}