package com.team23.trojanrideshare.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.team23.trojanrideshare.databinding.FragmentLoginBinding;
import com.team23.trojanrideshare.service.AccountService;
import com.team23.trojanrideshare.ui.common.BaseFragment;

import java.util.Map;

/** FR-1.4 Log In. */
public class LogInFragment extends BaseFragment {

    private FragmentLoginBinding b;
    private AuthViewModel vm;

    @Override
    protected String title() {
        return "Log In";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentLoginBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(AuthViewModel.class);
        observeCommon(vm, b.progress);
        vm.busy().observe(getViewLifecycleOwner(), busy -> b.logIn.setEnabled(!Boolean.TRUE.equals(busy)));
        vm.user().observe(getViewLifecycleOwner(), ev -> {
            if (ev != null && ev.take() != null) main().onSignedIn();
        });
        b.backend.setText("Data: " + vm.backendLabel());
        b.logIn.setOnClickListener(v -> submit());
        b.password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        b.signUp.setOnClickListener(v -> push(new SignUpFragment()));
    }

    private void submit() {
        hideKeyboard();
        b.emailLayout.setError(null);
        b.passwordLayout.setError(null);
        vm.logIn(String.valueOf(b.email.getText()), String.valueOf(b.password.getText()));
    }

    @Override
    protected boolean onFieldErrors(Map<String, String> errors) {
        b.emailLayout.setError(errors.get(AccountService.F_EMAIL));
        b.passwordLayout.setError(errors.get(AccountService.F_PASSWORD));
        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
