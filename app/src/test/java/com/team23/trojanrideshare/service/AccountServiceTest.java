package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.team23.trojanrideshare.data.local.LocalAuthGateway;
import com.team23.trojanrideshare.data.local.LocalDatabase;
import com.team23.trojanrideshare.data.local.LocalStore;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.domain.util.DeviceClock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Person 1 - White-box tests for AccountService (FR-1: registration, duplicates, nickname
 * display).
 *
 * This is a plain JUnit test under app/src/test, so it never touches Android. It runs against
 * the SAME on-device backend classes the app itself falls back to when no Firebase project is
 * configured (LocalAuthGateway + LocalDatabase + LocalStore), with the store's background/main
 * executors both swapped for an "run on this thread" executor so every async callback fires
 * synchronously, before the call that triggered it returns. That means these tests can call
 * service.signUp(...) and immediately read the result with no waiting, no mocking framework,
 * and no changes to the production code.
 *
 * Location in project: app/src/test/java/com/team23/trojanrideshare/service/AccountServiceTest.java
 * Class under test:   app/src/main/java/com/team23/trojanrideshare/service/AccountService.java
 */
public class AccountServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private AccountService service;
    private DeviceClock clock;

    @Before
    public void setUp() throws Exception {
        Executor immediate = Runnable::run;
        File dbFile = new File(tmp.getRoot(), "trojanrideshare-db.json");
        LocalStore store = new LocalStore(dbFile, immediate, immediate);
        clock = new DeviceClock();
        LocalDatabase db = new LocalDatabase(store, clock);
        LocalAuthGateway auth = new LocalAuthGateway(store, new LocalAuthGateway.SavedLogin() {
            private String savedUid;

            @Override
            public String get() {
                return savedUid;
            }

            @Override
            public void set(String uid) {
                this.savedUid = uid;
            }
        });
        service = new AccountService(auth, db, clock, new Session());
    }

    private static User form(String fullName, String email, String uscId, int gradYear) {
        User u = new User();
        u.setFullName(fullName);
        u.setEmail(email);
        u.setUscId(uscId);
        u.setGradYear(gradYear);
        return u;
    }

    // --------------------------------------------------------------------------------
    // Test 1 - FR-1.2.3: sign-up rejects an email that isn't a @usc.edu address.
    // --------------------------------------------------------------------------------
    @Test
    public void signUpRejectsNonUscEmail() {
        User form = form("Jane Trojan", "jane.trojan@gmail.com", "1234567890", clock.now().getYear());

        Map<String, String> errors = service.validate(form, "password1");

        assertTrue("expected an email error for a non-USC address",
                errors.containsKey(AccountService.F_EMAIL));
    }

    // --------------------------------------------------------------------------------
    // Test 2 - FR-1.2.4: sign-up rejects a USC ID that isn't exactly 10 digits.
    // --------------------------------------------------------------------------------
    @Test
    public void signUpRejectsMalformedUscId() {
        User tooShort = form("Jane Trojan", "jane@usc.edu", "12345", clock.now().getYear());
        assertTrue("expected a USC ID error for a 5-digit ID",
                service.validate(tooShort, "password1").containsKey(AccountService.F_USC_ID));

        User notDigits = form("Jane Trojan", "jane@usc.edu", "12345ABCDE", clock.now().getYear());
        assertTrue("expected a USC ID error for an ID containing letters",
                service.validate(notDigits, "password1").containsKey(AccountService.F_USC_ID));
    }

    // --------------------------------------------------------------------------------
    // Test 3 - FR-1.2.5: only students graduating this year through MAX_YEARS_AHEAD years
    // from now count as "active"; this is a boundary-value test on both ends of that range.
    // --------------------------------------------------------------------------------
    @Test
    public void signUpEnforcesActiveGradYearRange() {
        int[] range = service.activeGradYears(); // {thisYear, thisYear + MAX_YEARS_AHEAD}

        User tooSoon = form("Jane Trojan", "jane@usc.edu", "1234567890", range[0] - 1);
        assertTrue("a year before the active range must be rejected",
                service.validate(tooSoon, "password1").containsKey(AccountService.F_GRAD_YEAR));

        User tooFar = form("Jane Trojan", "jane@usc.edu", "1234567890", range[1] + 1);
        assertTrue("a year after the active range must be rejected",
                service.validate(tooFar, "password1").containsKey(AccountService.F_GRAD_YEAR));

        User lowerBoundary = form("Jane Trojan", "jane@usc.edu", "1234567890", range[0]);
        assertFalse("the first active year (this year) must be accepted",
                service.validate(lowerBoundary, "password1").containsKey(AccountService.F_GRAD_YEAR));

        User upperBoundary = form("Jane Trojan", "jane@usc.edu", "1234567890", range[1]);
        assertFalse("the last active year must be accepted",
                service.validate(upperBoundary, "password1").containsKey(AccountService.F_GRAD_YEAR));
    }

    // --------------------------------------------------------------------------------
    // Test 4 - FR-1.3: sign-up is rejected if the email OR the USC ID is already registered,
    // and each duplicate is reported against its own field.
    // --------------------------------------------------------------------------------
    @Test
    public void signUpRejectsDuplicateEmailAndDuplicateUscId() {
        User first = form("Jane Trojan", "dup@usc.edu", "1111111111", clock.now().getYear());
        signUpAndExpectSuccess(first, "password1");

        User sameEmail = form("John Trojan", "dup@usc.edu", "2222222222", clock.now().getYear());
        AppException emailDup = signUpAndExpectError(sameEmail, "password1");
        assertEquals(ErrorCode.DUPLICATE, emailDup.getCode());
        assertTrue("duplicate email must be reported on the email field",
                emailDup.getFieldErrors().containsKey(AccountService.F_EMAIL));

        User sameId = form("Jim Trojan", "other@usc.edu", "1111111111", clock.now().getYear());
        AppException idDup = signUpAndExpectError(sameId, "password1");
        assertEquals(ErrorCode.DUPLICATE, idDup.getCode());
        assertTrue("duplicate USC ID must be reported on the USC ID field",
                idDup.getFieldErrors().containsKey(AccountService.F_USC_ID));
    }

    // --------------------------------------------------------------------------------
    // Test 5 - FR-1.2.2: a user's display name falls back to their full name whenever no
    // nickname was given (including a blank/whitespace-only nickname), and uses the nickname
    // once one is set.
    // --------------------------------------------------------------------------------
    @Test
    public void displayNameFallsBackToFullNameWhenNicknameIsBlank() {
        User u = form("Jane Trojan", "jane@usc.edu", "1234567890", clock.now().getYear());
        assertEquals("Jane Trojan", u.getDisplayName());

        u.setNickname("   ");
        assertEquals("Jane Trojan", u.getDisplayName());

        u.setNickname("Janey");
        assertEquals("Janey", u.getDisplayName());
    }

    // --------------------------------------------------------------------------------
    // helpers
    // --------------------------------------------------------------------------------

    private void signUpAndExpectSuccess(User form, String password) {
        AppException[] error = new AppException[1];
        service.signUp(form, password, new Callback<User>() {
            @Override
            public void onSuccess(User u) {
            }

            @Override
            public void onError(AppException e) {
                error[0] = e;
            }
        });
        if (error[0] != null) fail("expected sign-up to succeed, but got: " + error[0].getMessage());
    }

    private AppException signUpAndExpectError(User form, String password) {
        User[] result = new User[1];
        AppException[] error = new AppException[1];
        service.signUp(form, password, new Callback<User>() {
            @Override
            public void onSuccess(User u) {
                result[0] = u;
            }

            @Override
            public void onError(AppException e) {
                error[0] = e;
            }
        });
        if (error[0] == null) {
            fail("expected sign-up to fail, but it succeeded with uid " + result[0].getUid());
        }
        return error[0];
    }
}
