package me.serenityline.api.auth.service;

import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PasswordPolicyService {

    public static final int PASSWORD_MIN_LENGTH = 10;
    public static final int PASSWORD_MAX_LENGTH = 128;

    private static final int MIN_ZXCVBN_SCORE = 2;

    public void validateRegistrationPassword(
            String password,
            String userName,
            String email
    ) {
        validatePassword(
                password,
                userName,
                email,
                "auth.password.required"
        );
    }

    public void validateChangePassword(
            String newPassword,
            String userName,
            String email
    ) {
        validatePassword(
                newPassword,
                userName,
                email,
                "auth.password.new.required"
        );
    }

    private void validatePassword(
            String password,
            String userName,
            String email,
            String requiredMessageKey
    ) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(requiredMessageKey);
        }

        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("auth.password.invalidLength");
        }

        Strength strength = new Zxcvbn().measure(
                password,
                buildUserInputs(userName, email)
        );

        if (strength.getScore() < MIN_ZXCVBN_SCORE) {
            throw new IllegalArgumentException("auth.password.tooWeak");
        }
    }

    private List<String> buildUserInputs(String userName, String email) {
        List<String> userInputs = new ArrayList<>();

        addIfPresent(userInputs, userName);
        addIfPresent(userInputs, email);
        addIfPresent(userInputs, extractEmailLocalPart(email));

        return userInputs;
    }

    private void addIfPresent(List<String> userInputs, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        userInputs.add(value.trim());
    }

    private String extractEmailLocalPart(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        int atIndex = email.indexOf('@');

        if (atIndex <= 0) {
            return null;
        }

        return email.substring(0, atIndex);
    }
}