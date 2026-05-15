package me.serenityline.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank(message = "{user.userName.required}")
                              @Size(max = 100, message = "{user.userName.tooLong}")
                              String userName,

                              @NotBlank(message = "{user.email.required}")
                              @Email(message = "{user.email.invalid}")
                              @Size(max = 320, message = "{user.email.tooLong}")
                              String email,

                              /*
                              Rigid password patterns are not enforced, such as requiring at least
                              one uppercase letter, one number, or one symbol.

                              These rules often lead to predictable passwords, such as "Password1!",
                              instead of actually stronger passwords.

                              For SerenityLine, a reasonable minimum length, free-form
                              passwords/passphrases, rate limiting, login attempt tracking,
                              secure BCrypt hashing, and 2FA for logins or sensitive actions are preferred.*/

                              /*(Italian)
                              Non vengono imposti pattern rigidi sulla password, come almeno una maiuscola,
                              un numero o un simbolo.

                              Queste regole spesso portano a password prevedibili, ad esempio
                              "Password1!", invece di password realmente più robuste.

                              Per SerenityLine è preferibile una lunghezza minima ragionevole,
                              password libere/passphrase, rate limiting, controllo dei tentativi,
                              hash sicuro con BCrypt e 2FA per login o azioni sensibili.
                               */


                              @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                              @NotBlank(message = "{auth.password.required}")
                              @Size(min = 10, max = 128, message = "{auth.password.invalidLength}")
                              String password,

                              @Pattern(
                                      regexp = "it-IT|en-US",
                                      message = "{user.preferredLocale.invalid}"
                              )
                              String preferredLocale,

                              Boolean wantsInvoice,

                              Boolean paymentEmailRemindersEnabled) {
}
