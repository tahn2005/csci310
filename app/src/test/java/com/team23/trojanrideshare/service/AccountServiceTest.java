package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Test;

import java.util.Map;

public class AccountServiceTest extends ServiceTestBase {

    private static User form(String email, String id, int year) {
        return new User("Tommy Trojan", "", email, id, year);
    }

    @Test
    public void validFormHasNoErrors() {
        assertTrue(accounts.validate(form("tommy@usc.edu", "1234567890", 2028), "secret1").isEmpty());
    }

    @Test
    public void emailMustEndWithUscEdu() {
        Map<String, String> e = accounts.validate(form("tommy@gmail.com", "1234567890", 2028), "secret1");
        assertTrue(e.containsKey(AccountService.F_EMAIL));
        e = accounts.validate(form("tommy@usc.edu.com", "1234567890", 2028), "secret1");
        assertTrue(e.containsKey(AccountService.F_EMAIL));
    }

    @Test
    public void uscIdMustBeTenDigits() {
        assertTrue(accounts.validate(form("t@usc.edu", "123456789", 2028), "secret1").containsKey(AccountService.F_USC_ID));
        assertTrue(accounts.validate(form("t@usc.edu", "12345678901", 2028), "secret1").containsKey(AccountService.F_USC_ID));
        assertTrue(accounts.validate(form("t@usc.edu", "12345abcde", 2028), "secret1").containsKey(AccountService.F_USC_ID));
    }

    @Test
    public void graduationYearMustShowActiveStudent() {
        assertTrue(accounts.validate(form("t@usc.edu", "1234567890", 2025), "secret1").containsKey(AccountService.F_GRAD_YEAR));
        assertFalse(accounts.validate(form("t@usc.edu", "1234567890", 2026), "secret1").containsKey(AccountService.F_GRAD_YEAR));
        assertFalse(accounts.validate(form("t@usc.edu", "1234567890", 2032), "secret1").containsKey(AccountService.F_GRAD_YEAR));
        assertTrue(accounts.validate(form("t@usc.edu", "1234567890", 2033), "secret1").containsKey(AccountService.F_GRAD_YEAR));
    }

    @Test
    public void missingFieldsAreAllReported() {
        Map<String, String> e = accounts.validate(new User("", "", "", "", 0), "");
        assertEquals(5, e.size());
    }

    @Test
    public void signUpSavesProfileAndDisplayNameDefaultsToFullName() {
        Results<User> r = new Results<>();
        accounts.signUp(form("Tommy@USC.edu", "1234567890", 2028), "secret1", r);
        User u = r.get();
        assertEquals("tommy@usc.edu", u.getEmail());
        assertEquals("Tommy Trojan", u.getDisplayName());
        assertEquals(u.getUid(), session.uid());
        assertTrue(db.users.containsKey(u.getUid()));
    }

    @Test
    public void duplicateEmailIsRejected() {
        accounts.signUp(form("tommy@usc.edu", "1234567890", 2028), "secret1", new Results<>());
        Results<User> r = new Results<>();
        accounts.signUp(form("tommy@usc.edu", "1111111111", 2028), "secret1", r);
        assertEquals(ErrorCode.DUPLICATE, r.err().getCode());
    }

    @Test
    public void duplicateUscIdIsRejectedAndAuthAccountRemoved() {
        accounts.signUp(form("tommy@usc.edu", "1234567890", 2028), "secret1", new Results<>());
        Results<User> r = new Results<>();
        accounts.signUp(form("other@usc.edu", "1234567890", 2028), "secret1", r);
        assertEquals(ErrorCode.DUPLICATE, r.err().getCode());
        assertTrue(r.err().getFieldErrors().containsKey(AccountService.F_USC_ID));
        assertEquals(1, auth.deleted);
        assertFalse(auth.passwords.containsKey("other@usc.edu"));
    }

    @Test
    public void logInLogOutAndBadPassword() {
        accounts.signUp(form("tommy@usc.edu", "1234567890", 2028), "secret1", new Results<>());
        accounts.logOut();
        assertNull(session.user());

        Results<User> bad = new Results<>();
        accounts.logIn("tommy@usc.edu", "wrong", bad);
        assertEquals(ErrorCode.NOT_ALLOWED, bad.err().getCode());

        Results<User> ok = new Results<>();
        accounts.logIn("tommy@usc.edu", "secret1", ok);
        assertEquals("Tommy Trojan", ok.get().getFullName());
    }

    @Test
    public void nicknameUpdateAndPublicRides() {
        as("alice");
        Results<User> r = new Results<>();
        accounts.updateNickname("  Ali ", r);
        assertEquals("Ali", r.get().getDisplayName());
        assertEquals("Ali", db.users.get("alice").getNickname());

        accounts.setRidePublic("ride1", true, new Results<>());
        assertTrue(db.users.get("alice").getPublicRideIds().contains("ride1"));
        accounts.setRidePublic("ride1", false, new Results<>());
        assertFalse(db.users.get("alice").getPublicRideIds().contains("ride1"));
    }
}
