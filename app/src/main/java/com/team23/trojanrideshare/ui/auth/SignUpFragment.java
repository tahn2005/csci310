package com.team23.trojanrideshare.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.team23.trojanrideshare.databinding.FragmentSignupBinding;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.service.AccountService;
import com.team23.trojanrideshare.ui.common.BaseFragment;

import java.util.Map;

/** FR-1.1 – FR-1.3 Sign Up, with an error under each invalid field (FR-1.E1 – E5). */
public class SignUpFragment extends BaseFragment {

    private FragmentSignupBinding b;
    private AuthViewModel vm;

    @Override
    protected String title() {
        return "Sign Up";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentSignupBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(AuthViewModel.class);
        observeCommon(vm, b.progress);
        vm.busy().observe(getViewLifecycleOwner(), busy -> b.create.setEnabled(!Boolean.TRUE.equals(busy)));
        vm.user().observe(getViewLifecycleOwner(), ev -> {
            User u = ev == null ? null : ev.take();
            if (u != null) main().onSignedIn();
        });
        int[] years = vm.activeGradYears();
        b.gradYearLayout.setHelperText("Active students graduate " + years[0] + "–" + years[1] + ".");
        b.create.setOnClickListener(v -> submit());
    }

    private void submit() {
        hideKeyboard();
        for (TextInputLayout l : new TextInputLayout[]{b.fullNameLayout, b.nicknameLayout, b.emailLayout,
                b.uscIdLayout, b.passwordLayout, b.gradYearLayout}) {
            l.setError(null);
        }
        int year;
        try {
            year = Integer.parseInt(String.valueOf(b.gradYear.getText()).trim());
        } catch (NumberFormatException e) {
            year = 0;
        }
        User u = new User(String.valueOf(b.fullName.getText()), String.valueOf(b.nickname.getText()),
                String.valueOf(b.email.getText()), String.valueOf(b.uscId.getText()), year);
        vm.signUp(u, String.valueOf(b.password.getText()));
    }

    @Override
    protected boolean onFieldErrors(Map<String, String> errors) {
        b.fullNameLayout.setError(errors.get(AccountService.F_FULL_NAME));
        b.nicknameLayout.setError(errors.get(AccountService.F_NICKNAME));
        b.emailLayout.setError(errors.get(AccountService.F_EMAIL));
        b.uscIdLayout.setError(errors.get(AccountService.F_USC_ID));
        b.passwordLayout.setError(errors.get(AccountService.F_PASSWORD));
        b.gradYearLayout.setError(errors.get(AccountService.F_GRAD_YEAR));
        if (errors.size() == 1) toast(errors.values().iterator().next());
        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
