package com.team23.trojanrideshare.ui.common;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.Place;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A location text field backed by the location API: typing shows suggestions, and only a
 * place picked from the suggestions counts as selected (FR-2.2.2, FR-3.2.1).
 */
public class PlaceField {

    /** Where suggestions come from (a ViewModel delegating to RideService). */
    public interface Source {
        void suggest(String query, Callback<List<Place>> cb);

        void resolve(Place suggestion, Callback<Place> cb);
    }

    private static final long DEBOUNCE_MS = 350;

    private final TextInputLayout layout;
    private final MaterialAutoCompleteTextView input;
    private final Source source;
    private final SuggestionAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingQuery;
    private Place selected;
    private boolean settingText;
    private Consumer<Place> onChange = p -> {
    };

    public PlaceField(TextInputLayout layout, MaterialAutoCompleteTextView input, Source source) {
        this.layout = layout;
        this.input = input;
        this.source = source;
        this.adapter = new SuggestionAdapter(input.getContext());
        input.setAdapter(adapter);
        input.setThreshold(2);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (settingText) return;
                if (selected != null) {
                    selected = null;
                    onChange.accept(null);
                }
                layout.setError(null);
                scheduleQuery(s.toString());
            }
        });
        input.setOnItemClickListener((parent, view, position, id) -> pick(adapter.getItem(position)));
    }

    public void setOnChange(Consumer<Place> listener) {
        onChange = listener == null ? p -> {
        } : listener;
    }

    private void scheduleQuery(String text) {
        if (pendingQuery != null) handler.removeCallbacks(pendingQuery);
        if (text.trim().length() < 2) {
            adapter.setItems(new ArrayList<>());
            return;
        }
        pendingQuery = () -> source.suggest(text, new Callback<List<Place>>() {
            @Override
            public void onSuccess(List<Place> result) {
                if (!text.equals(input.getText().toString()) || selected != null) return;
                adapter.setItems(result);
                if (input.hasFocus() && !result.isEmpty() && input.isAttachedToWindow()) input.showDropDown();
            }

            @Override
            public void onError(AppException e) {
                layout.setError(e.getMessage());
            }
        });
        handler.postDelayed(pendingQuery, DEBOUNCE_MS);
    }

    private void pick(Place suggestion) {
        if (suggestion == null) return;
        source.resolve(suggestion, new Callback<Place>() {
            @Override
            public void onSuccess(Place p) {
                setSelected(p);
                input.dismissDropDown();
            }

            @Override
            public void onError(AppException e) {
                layout.setError(e.getMessage());
            }
        });
    }

    public Place getSelected() {
        return selected;
    }

    public String getText() {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    public void setSelected(Place p) {
        selected = p;
        settingText = true;
        input.setText(p == null ? "" : p.getName(), false);
        settingText = false;
        layout.setError(null);
        layout.setHelperText(p == null ? null : p.getAddress().isEmpty() ? null : p.getAddress());
        onChange.accept(p);
    }

    public void clear() {
        setSelected(null);
    }

    public void setError(String error) {
        layout.setError(error);
    }

    /**
     * The selected place; if the user typed text without picking a suggestion, the best
     * suggestion is used. Delivers null if the field is empty.
     */
    public void resolveTyped(Callback<Place> cb) {
        if (selected != null) {
            cb.onSuccess(selected);
            return;
        }
        String text = getText();
        if (text.isEmpty()) {
            cb.onSuccess(null);
            return;
        }
        source.suggest(text, new Callback<List<Place>>() {
            @Override
            public void onSuccess(List<Place> result) {
                if (result.isEmpty()) {
                    AppException e = new AppException(ErrorCode.NOT_FOUND,
                            "Location not found. Please try a different search.");
                    layout.setError(e.getMessage());
                    cb.onError(e);
                    return;
                }
                source.resolve(result.get(0), new Callback<Place>() {
                    @Override
                    public void onSuccess(Place p) {
                        setSelected(p);
                        cb.onSuccess(p);
                    }

                    @Override
                    public void onError(AppException e) {
                        layout.setError(e.getMessage());
                        cb.onError(e);
                    }
                });
            }

            @Override
            public void onError(AppException e) {
                layout.setError(e.getMessage());
                cb.onError(e);
            }
        });
    }

    /** Two-line suggestion rows; the list is already filtered by the API so no local filtering. */
    private static class SuggestionAdapter extends ArrayAdapter<Place> {

        private final List<Place> items = new ArrayList<>();

        SuggestionAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_2);
        }

        void setItems(List<Place> places) {
            items.clear();
            items.addAll(places);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Place getItem(int position) {
            return position >= 0 && position < items.size() ? items.get(position) : null;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            Place p = items.get(position);
            ((TextView) v.findViewById(android.R.id.text1)).setText(p.getName());
            TextView sub = v.findViewById(android.R.id.text2);
            sub.setText(p.getAddress());
            sub.setVisibility(p.getAddress().isEmpty() ? View.GONE : View.VISIBLE);
            return v;
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults r = new FilterResults();
                    r.values = items;
                    r.count = items.size();
                    return r;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }

                @Override
                public CharSequence convertResultToString(Object resultValue) {
                    return resultValue instanceof Place ? ((Place) resultValue).getName() : "";
                }
            };
        }
    }
}
