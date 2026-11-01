package com.team23.trojanrideshare.testutil;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.gateway.AuthGateway;

import java.util.HashMap;
import java.util.Map;

public class FakeAuthGateway implements AuthGateway {

    public final Map<String, String> passwords = new HashMap<>();
    public final Map<String, String> uids = new HashMap<>();
    public String current;
    public int deleted;
    private int next = 1;

    @Override
    public void createAccount(String email, String password, Callback<String> cb) {
        if (passwords.containsKey(email)) {
            cb.onError(new AppException(ErrorCode.DUPLICATE, "An account with this email already exists."));
            return;
        }
        String uid = "u" + (next++);
        passwords.put(email, password);
        uids.put(email, uid);
        current = uid;
        cb.onSuccess(uid);
    }

    @Override
    public void signIn(String email, String password, Callback<String> cb) {
        if (password.equals(passwords.get(email))) {
            current = uids.get(email);
            cb.onSuccess(current);
        } else {
            cb.onError(new AppException(ErrorCode.NOT_ALLOWED, "Incorrect email or password."));
        }
    }

    @Override
    public void signOut() {
        current = null;
    }

    @Override
    public void deleteAccount(Callback<Void> cb) {
        String email = null;
        for (Map.Entry<String, String> e : uids.entrySet()) if (e.getValue().equals(current)) email = e.getKey();
        if (email != null) {
            passwords.remove(email);
            uids.remove(email);
        }
        deleted++;
        current = null;
        cb.onSuccess(null);
    }

    @Override
    public String currentUserId() {
        return current;
    }
}
