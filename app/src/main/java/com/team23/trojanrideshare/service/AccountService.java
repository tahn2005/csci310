package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.data.UserRepository;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.domain.util.Clock;
import com.team23.trojanrideshare.domain.util.SafeLogger;
import com.team23.trojanrideshare.gateway.AuthGateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** FR-1: registration rules, log in / log out, nickname, and public ride choices. */
public class AccountService {

    public static final String F_FULL_NAME = "fullName";
    public static final String F_NICKNAME = "nickname";
    public static final String F_EMAIL = "email";
    public static final String F_USC_ID = "uscId";
    public static final String F_PASSWORD = "password";
    public static final String F_GRAD_YEAR = "gradYear";

    /** An active student graduates this year or within the next six years. */
    public static final int MAX_YEARS_AHEAD = 6;
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_NICKNAME_LENGTH = 30;

    private static final Pattern USC_EMAIL = Pattern.compile("^[a-z0-9._%+-]+@usc\\.edu$");
    private static final Pattern USC_ID = Pattern.compile("^\\d{10}$");

    private final AuthGateway auth;
    private final UserRepository users;
    private final Clock clock;
    private final Session session;

    public AccountService(AuthGateway auth, UserRepository users, Clock clock, Session session) {
        this.auth = auth;
        this.users = users;
        this.clock = clock;
        this.session = session;
    }

    public Session session() {
        return session;
    }

    public User currentUser() {
        return session.user();
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.US);
    }

    /** Range of graduation years that count as an active student (FR-1.2.5). */
    public int[] activeGradYears() {
        int year = clock.now().getYear();
        return new int[]{year, year + MAX_YEARS_AHEAD};
    }

    /** FR-1.2: one message per invalid field; empty if the form is valid. */
    public Map<String, String> validate(User u, String password) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (u.getFullName() == null || u.getFullName().trim().isEmpty()) {
            errors.put(F_FULL_NAME, "Enter your full name.");
        }
        if (u.getNickname() != null && u.getNickname().trim().length() > MAX_NICKNAME_LENGTH) {
            errors.put(F_NICKNAME, "Nickname must be " + MAX_NICKNAME_LENGTH + " characters or fewer.");
        }
        String email = normalizeEmail(u.getEmail());
        if (email.isEmpty()) {
            errors.put(F_EMAIL, "Enter your USC email address.");
        } else if (!USC_EMAIL.matcher(email).matches()) {
            errors.put(F_EMAIL, "A valid USC email address ending in @usc.edu is required.");
        }
        String id = u.getUscId() == null ? "" : u.getUscId().trim();
        if (id.isEmpty()) {
            errors.put(F_USC_ID, "Enter your USC ID.");
        } else if (!USC_ID.matcher(id).matches()) {
            errors.put(F_USC_ID, "A valid 10-digit USC ID is required.");
        }
        if (password == null || password.isEmpty()) {
            errors.put(F_PASSWORD, "Enter a password.");
        } else if (password.length() < MIN_PASSWORD_LENGTH) {
            errors.put(F_PASSWORD, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        int[] range = activeGradYears();
        if (u.getGradYear() <= 0) {
            errors.put(F_GRAD_YEAR, "Enter your expected graduation year.");
        } else if (u.getGradYear() < range[0] || u.getGradYear() > range[1]) {
            errors.put(F_GRAD_YEAR, "TrojanRideshare is only available to active USC students (graduating "
                    + range[0] + "–" + range[1] + ").");
        }
        return errors;
    }

    /** SD-1 Sign Up: validate, create the credentials, then save the profile and USC ID. */
    public void signUp(User form, String password, Callback<User> cb) {
        Map<String, String> errors = validate(form, password);
        if (!errors.isEmpty()) {
            cb.onError(AppException.validation(errors));
            return;
        }
        User u = new User(form.getFullName().trim(),
                form.getNickname() == null ? "" : form.getNickname().trim(),
                normalizeEmail(form.getEmail()), form.getUscId().trim(), form.getGradYear());
        auth.createAccount(u.getEmail(), password, new Callback<String>() {
            @Override
            public void onSuccess(String uid) {
                u.setUid(uid);
                users.createUser(u, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void v) {
                        session.set(u);
                        SafeLogger.log("Signed up " + u.getEmail());
                        cb.onSuccess(u);
                    }

                    @Override
                    public void onError(AppException e) {
                        AppException reported = e;
                        if (e.getCode() == ErrorCode.DUPLICATE) {
                            Map<String, String> f = new LinkedHashMap<>();
                            f.put(F_USC_ID, e.getMessage());
                            reported = new AppException(ErrorCode.DUPLICATE, e.getMessage(), f, e);
                        }
                        AppException result = reported;
                        // Undo the half-finished registration so it can't be used to log in.
                        auth.deleteAccount(new Callback<Void>() {
                            @Override
                            public void onSuccess(Void v) {
                                cb.onError(result);
                            }

                            @Override
                            public void onError(AppException ignored) {
                                cb.onError(result);
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(AppException e) {
                if (e.getCode() == ErrorCode.DUPLICATE) {
                    Map<String, String> f = new LinkedHashMap<>();
                    f.put(F_EMAIL, e.getMessage());
                    cb.onError(new AppException(ErrorCode.DUPLICATE, e.getMessage(), f, e));
                } else {
                    cb.onError(e);
                }
            }
        });
    }

    /** FR-1.4: access is granted only once a profile exists for the account. */
    public void logIn(String email, String password, Callback<User> cb) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (normalizeEmail(email).isEmpty()) errors.put(F_EMAIL, "Enter your USC email address.");
        if (password == null || password.isEmpty()) errors.put(F_PASSWORD, "Enter your password.");
        if (!errors.isEmpty()) {
            cb.onError(AppException.validation(errors));
            return;
        }
        auth.signIn(normalizeEmail(email), password, new Callback<String>() {
            @Override
            public void onSuccess(String uid) {
                loadProfile(uid, cb);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** Reopens the session of a user who is still signed in; returns null if nobody is. */
    public void restoreSession(Callback<User> cb) {
        String uid = auth.currentUserId();
        if (uid == null) {
            session.clear();
            cb.onSuccess(null);
            return;
        }
        loadProfile(uid, cb);
    }

    /** True if Firebase remembers a login, even before {@link #restoreSession} has run. */
    public boolean hasSavedLogin() {
        return auth.currentUserId() != null;
    }

    private void loadProfile(String uid, Callback<User> cb) {
        users.getUser(uid, new Callback<User>() {
            @Override
            public void onSuccess(User u) {
                session.set(u);
                cb.onSuccess(u);
            }

            @Override
            public void onError(AppException e) {
                if (e.getCode() == ErrorCode.NOT_FOUND) {
                    auth.signOut();
                    session.clear();
                    cb.onError(new AppException(ErrorCode.NOT_FOUND,
                            "This account's registration was not completed. Please sign up again."));
                } else {
                    cb.onError(e);
                }
            }
        });
    }

    /** FR-1.5. */
    public void logOut() {
        auth.signOut();
        session.clear();
    }

    /** Loads any user's profile (for public profiles) or refreshes the signed-in user's. */
    public void getUser(String uid, Callback<User> cb) {
        users.getUser(uid, new Callback<User>() {
            @Override
            public void onSuccess(User u) {
                if (u.getUid().equals(session.uid())) session.set(u);
                cb.onSuccess(u);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** FR-1.7: a blank nickname falls back to the full name (FR-1.2.2). */
    public void updateNickname(String name, Callback<User> cb) {
        User me = session.user();
        if (me == null) {
            cb.onError(AppException.notAllowed("Please log in again."));
            return;
        }
        String nick = name == null ? "" : name.trim();
        if (nick.length() > MAX_NICKNAME_LENGTH) {
            Map<String, String> f = new LinkedHashMap<>();
            f.put(F_NICKNAME, "Nickname must be " + MAX_NICKNAME_LENGTH + " characters or fewer.");
            cb.onError(AppException.validation(f));
            return;
        }
        User updated = copy(me);
        updated.setNickname(nick);
        users.updateUser(updated, new Callback<Void>() {
            @Override
            public void onSuccess(Void v) {
                session.set(updated);
                cb.onSuccess(updated);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** FR-1.10: choose whether a ride appears on the user's public profile. */
    public void setRidePublic(String rideId, boolean on, Callback<User> cb) {
        User me = session.user();
        if (me == null) {
            cb.onError(AppException.notAllowed("Please log in again."));
            return;
        }
        User updated = copy(me);
        List<String> ids = new ArrayList<>(updated.getPublicRideIds());
        ids.remove(rideId);
        if (on) ids.add(rideId);
        updated.setPublicRideIds(ids);
        users.updateUser(updated, new Callback<Void>() {
            @Override
            public void onSuccess(Void v) {
                session.set(updated);
                cb.onSuccess(updated);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    private static User copy(User u) {
        User c = new User(u.getFullName(), u.getNickname(), u.getEmail(), u.getUscId(), u.getGradYear());
        c.setUid(u.getUid());
        c.setPublicRideIds(u.getPublicRideIds());
        return c;
    }
}
