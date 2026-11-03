package com.team23.trojanrideshare.ui.common;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.ui.AppContainer;
import com.team23.trojanrideshare.ui.MainActivity;
import com.team23.trojanrideshare.ui.TrojanRideshareApp;

import java.util.Map;

/** Shared behaviour for every screen: title, navigation, busy indicator, errors, dialogs. */
public abstract class BaseFragment extends Fragment {

    /** Toolbar title for this screen. */
    protected abstract String title();

    @Override
    public void onResume() {
        super.onResume();
        main().setScreenTitle(title());
    }

    protected MainActivity main() {
        return (MainActivity) requireActivity();
    }

    protected AppContainer app() {
        return ((TrojanRideshareApp) requireActivity().getApplication()).container();
    }

    protected void push(Fragment f) {
        main().push(f);
    }

    protected void toast(String text) {
        if (getView() == null || text == null) return;
        Snackbar s = Snackbar.make(getView(), text, Snackbar.LENGTH_LONG);
        View nav = main().bottomNavIfVisible();
        if (nav != null) s.setAnchorView(nav);
        s.show();
    }

    protected void hideKeyboard() {
        View v = getView();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        View focus = requireActivity().getCurrentFocus();
        if (focus != null) focus.clearFocus();
    }

    /** Wires the busy flag, errors, and messages of a ViewModel to this screen. */
    protected void observeCommon(BaseViewModel vm, View progress) {
        vm.busy().observe(getViewLifecycleOwner(), b -> {
            if (progress != null) progress.setVisibility(Boolean.TRUE.equals(b) ? View.VISIBLE : View.GONE);
        });
        vm.error().observe(getViewLifecycleOwner(), ev -> {
            AppException e = ev == null ? null : ev.take();
            if (e == null) return;
            if (!e.getFieldErrors().isEmpty() && onFieldErrors(e.getFieldErrors())) return;
            toast(e.getMessage());
        });
        vm.message().observe(getViewLifecycleOwner(), ev -> {
            String m = ev == null ? null : ev.take();
            if (m != null) toast(m);
        });
    }

    /** Clears a field's error as soon as the user edits it, so a fixed field stops showing red. */
    protected static void clearErrorOnEdit(TextInputLayout... layouts) {
        for (TextInputLayout layout : layouts) {
            EditText input = layout.getEditText();
            if (input == null) continue;
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (layout.getError() != null) layout.setError(null);
                }
            });
        }
    }

    /** Shows per-field errors; return true if they were all shown. */
    protected boolean onFieldErrors(Map<String, String> errors) {
        return false;
    }

    protected void confirm(String title, String message, String positive, Runnable onYes) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Back", null)
                .setPositiveButton(positive, (d, w) -> onYes.run())
                .show();
    }
}
