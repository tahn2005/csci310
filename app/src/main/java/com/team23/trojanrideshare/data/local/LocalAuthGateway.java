package com.team23.trojanrideshare.data.local;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.gateway.AuthGateway;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Accounts kept in the on-device database. Passwords are stored only as salted PBKDF2 hashes
 * (NFR-2). The signed-in user id is remembered so the app reopens signed in, as with Firebase.
 */
public class LocalAuthGateway implements AuthGateway {

    /** Where the signed-in user id is remembered between runs (SharedPreferences in the app). */
    public interface SavedLogin {
        String get();

        void set(String uid);
    }

    static final String ACCOUNTS = "accounts";
    private static final int ITERATIONS = 20000;
    private static final int KEY_BITS = 256;

    private final LocalStore store;
    private final SavedLogin savedLogin;
    private final SecureRandom random = new SecureRandom();

    public LocalAuthGateway(LocalStore store, SavedLogin savedLogin) {
        this.store = store;
        this.savedLogin = savedLogin;
    }

    @Override
    public void createAccount(String email, String password, Callback<String> cb) {
        store.run(s -> {
            if (s.get(ACCOUNTS, email) != null) {
                throw new AppException(ErrorCode.DUPLICATE, "An account with this email already exists.");
            }
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            Map<String, Object> account = new HashMap<>();
            String uid = s.newId();
            account.put("uid", uid);
            account.put("salt", Base64.getEncoder().encodeToString(salt));
            account.put("hash", Base64.getEncoder().encodeToString(hash(password, salt)));
            account.put("iterations", ITERATIONS);
            s.put(ACCOUNTS, email, account);
            return uid;
        }, signedIn(cb));
    }

    @Override
    public void signIn(String email, String password, Callback<String> cb) {
        store.run(s -> {
            Map<String, Object> account = s.get(ACCOUNTS, email);
            if (account == null || !matches(account, password)) {
                throw new AppException(ErrorCode.NOT_ALLOWED, "Incorrect email or password.");
            }
            return (String) account.get("uid");
        }, signedIn(cb));
    }

    @Override
    public void signOut() {
        savedLogin.set(null);
    }

    @Override
    public void deleteAccount(Callback<Void> cb) {
        String uid = savedLogin.get();
        savedLogin.set(null);
        store.run(s -> {
            for (Map.Entry<String, Map<String, Object>> e : s.all(ACCOUNTS).entrySet()) {
                if (e.getValue().get("uid").equals(uid)) s.delete(ACCOUNTS, e.getKey());
            }
            return null;
        }, cb);
    }

    @Override
    public String currentUserId() {
        return savedLogin.get();
    }

    private Callback<String> signedIn(Callback<String> cb) {
        return new Callback<String>() {
            @Override
            public void onSuccess(String uid) {
                savedLogin.set(uid);
                cb.onSuccess(uid);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        };
    }

    private static boolean matches(Map<String, Object> account, String password) throws AppException {
        byte[] salt = Base64.getDecoder().decode((String) account.get("salt"));
        byte[] expected = Base64.getDecoder().decode((String) account.get("hash"));
        return MessageDigest.isEqual(expected, hash(password, salt));
    }

    private static byte[] hash(String password, byte[] salt) throws AppException {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new AppException(ErrorCode.UNKNOWN, "Couldn't secure the password on this device.", e);
        }
    }
}
