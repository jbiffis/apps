package com.biffis.tracker.cli;

import com.biffis.tracker.model.User;
import com.biffis.tracker.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One-shot admin tool: {@code java -jar app.jar set-password <email>}.
 * Reads the new password from stdin (no echo when a console is attached),
 * bcrypts it, updates the row, clears the force-change flag, exits. Never
 * logs or echoes the password.
 *
 * Only acts when the first program argument is {@code set-password}; on a
 * normal boot it's a no-op. The CLI run uses {@code WebApplicationType.NONE}
 * (see {@code TrackerApplication.main}) so it doesn't bind port 8080.
 */
@Component
public class SetPasswordRunner implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public SetPasswordRunner(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> nonOption = args.getNonOptionArgs();
        if (nonOption.isEmpty() || !nonOption.get(0).equals("set-password")) {
            return; // normal boot
        }
        if (nonOption.size() < 2) {
            System.err.println("usage: set-password <email>");
            exit(2);
            return;
        }
        String email = nonOption.get(1);

        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            // No PII: email is an account handle, not health data, and the
            // operator typed it. Safe to name it back so they know it missed.
            System.err.println("No such user: " + email);
            exit(1);
            return;
        }

        char[] pw = readPassword();
        try {
            if (pw == null || pw.length == 0) {
                System.err.println("Empty password; aborting.");
                exit(1);
                return;
            }
            user.setPasswordHash(passwordEncoder.encode(new String(pw)));
            user.setMustChangePassword(false);
            users.save(user);
            System.out.println("Password updated for " + email + ".");
            exit(0);
        } finally {
            if (pw != null) {
                java.util.Arrays.fill(pw, '\0');
            }
        }
    }

    private char[] readPassword() {
        Console console = System.console();
        if (console != null) {
            return console.readPassword("New password for the account: ");
        }
        // No TTY (e.g. piped stdin). Read one line without echo guarantees.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? null : line.toCharArray();
        } catch (IOException e) {
            return null;
        }
    }

    private void exit(int code) {
        System.exit(code);
    }
}
