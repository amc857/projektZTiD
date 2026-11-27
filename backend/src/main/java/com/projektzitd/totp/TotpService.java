package com.projektzitd.totp;

import com.projektzitd.user.User;
import com.projektzitd.user.UserRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TOTP service — implements RFC 6238 (Time-based One-Time Password).
 *
 * Algorithm summary:
 *   1. Shared secret S generated with SecureRandom (160-bit, Base32-encoded).
 *   2. Counter T = floor(currentUnixTime / 30)  [30-second time step].
 *   3. HMAC-SHA1(S, T) truncated to 6 decimal digits = OTP.
 *   4. Verification window: T-1 … T+1 (±30 s) to account for clock drift.
 */
@Service
@RequiredArgsConstructor
public class TotpService {

    private static final String ISSUER = "ZTiD-FileShare";
    private static final int TIME_STEP = 30;    // seconds
    private static final int CODE_DIGITS = 6;
    private static final int ALLOWED_SKEW = 1;  // ±1 time step

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private static final Duration REPLAY_WINDOW = Duration.ofSeconds(90); // (2*skew+1) * step

    private final UserRepository userRepository;

    /** Failed-attempt counters per user (in memory, single backend instance). */
    private final Map<String, FailState> failures = new ConcurrentHashMap<>();
    /** Last accepted code per user, to reject replay of the same code. */
    private final Map<String, UsedCode> lastUsed = new ConcurrentHashMap<>();

    private static final class FailState { int count; Instant lockedUntil; }
    private record UsedCode(String code, Instant at) {}

    // ── Secret generation ─────────────────────────────────────────────────────

    /**
     * Generates a new Base32 TOTP secret and stores it (unconfirmed) for the user.
     * The secret is NOT yet active — user must call enable() after verifying one code.
     *
     * @return Base32-encoded secret (pass to buildOtpauthUri)
     */
    @Transactional
    public String generateAndStoreSecret(String username) {
        SecretGenerator generator = new DefaultSecretGenerator(32); // 32 chars ≈ 160 bits
        String secret = generator.generate();

        User user = getUser(username);
        if (user.isTotpEnabled()) {
            throw new IllegalStateException("TOTP already enabled");
        }
        user.setTotpSecret(secret);
        user.setTotpEnabled(false); // not enabled until first code verified
        userRepository.save(user);
        return secret;
    }

    /**
     * Builds the otpauth:// URI for QR code generation.
     * Format: otpauth://totp/ISSUER:username?secret=S&issuer=ISSUER&digits=6&period=30
     */
    public String buildOtpauthUri(String username, String secret) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(CODE_DIGITS)
                .period(TIME_STEP)
                .build();
        return data.getUri();
    }

    // ── Verification ──────────────────────────────────────────────────────────

    /**
     * Verifies a 6-digit TOTP code against the user's stored secret.
     * Uses a ±1 time-step window to tolerate clock drift.
     *
     * @return true if code is valid within the allowed window
     */
    public boolean verifyCode(String username, String code) {
        User user = getUser(username);
        if (user.getTotpSecret() == null) return false;
        return checkThrottled(username, user.getTotpSecret(), code);
    }

    /** True while the user is locked out after too many failed codes. */
    public boolean isLocked(String username) {
        FailState f = failures.get(username);
        return f != null && f.lockedUntil != null && Instant.now().isBefore(f.lockedUntil);
    }

    // ── Enable / Disable ─────────────────────────────────────────────────────

    /**
     * Activates TOTP for the user after they verify the first code from their authenticator.
     * Returns false (and does NOT enable) if the code is wrong.
     */
    @Transactional
    public boolean enable(String username, String code) {
        User user = getUser(username);
        if (user.getTotpSecret() == null) return false;
        if (!checkThrottled(username, user.getTotpSecret(), code)) return false;

        user.setTotpEnabled(true);
        userRepository.save(user);
        return true;
    }

    /**
     * Disables TOTP after verifying a valid code (prevents accidental disable).
     * Clears the stored secret.
     */
    @Transactional
    public boolean disable(String username, String code) {
        User user = getUser(username);
        if (!user.isTotpEnabled()) return false;
        if (!checkThrottled(username, user.getTotpSecret(), code)) return false;

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        return true;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Verifies a code with brute-force and replay protection:
     * 5 failures lock the user for 5 minutes; a code accepted once cannot be reused.
     */
    private synchronized boolean checkThrottled(String username, String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        if (isLocked(username)) return false;

        UsedCode prev = lastUsed.get(username);
        boolean replay = prev != null && prev.code().equals(code)
                && Instant.now().isBefore(prev.at().plus(REPLAY_WINDOW));

        if (!replay && isValidCode(secret, code)) {
            failures.remove(username);
            lastUsed.put(username, new UsedCode(code, Instant.now()));
            return true;
        }

        FailState f = failures.computeIfAbsent(username, k -> new FailState());
        if (++f.count >= MAX_FAILED_ATTEMPTS) {
            f.lockedUntil = Instant.now().plus(LOCK_DURATION);
            f.count = 0;
        }
        return false;
    }

    private boolean isValidCode(String secret, String code) {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, CODE_DIGITS);
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // DefaultCodeVerifier uses ±1 window internally when allowedTimePeriodDiscrepancy > 0
        ((DefaultCodeVerifier) verifier).setAllowedTimePeriodDiscrepancy(ALLOWED_SKEW);
        return verifier.isValidCode(secret, code);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
