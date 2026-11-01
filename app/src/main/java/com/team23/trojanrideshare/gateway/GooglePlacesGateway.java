package com.team23.trojanrideshare.gateway;

import android.content.Context;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.util.SafeLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Location API backed by the Google Places SDK. Used when a Places API key is configured
 * (places.apiKey in local.properties); otherwise the app uses {@link OpenStreetMapGateway}.
 */
public class GooglePlacesGateway implements LocationGateway {

    /** Bias suggestions toward Greater Los Angeles. */
    private static final RectangularBounds LA_BOUNDS = RectangularBounds.newInstance(
            new LatLng(33.60, -118.70), new LatLng(34.35, -117.70));

    private final PlacesClient client;
    private AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();

    public GooglePlacesGateway(Context context, String apiKey) {
        if (!Places.isInitialized()) {
            Places.initialize(context.getApplicationContext(), apiKey);
        }
        client = Places.createClient(context.getApplicationContext());
    }

    @Override
    public void suggest(String query, Callback<List<Place>> cb) {
        if (query == null || query.trim().length() < 2) {
            cb.onSuccess(new ArrayList<>());
            return;
        }
        FindAutocompletePredictionsRequest req = FindAutocompletePredictionsRequest.builder()
                .setQuery(query.trim())
                .setSessionToken(token)
                .setLocationBias(LA_BOUNDS)
                .build();
        client.findAutocompletePredictions(req)
                .addOnSuccessListener(resp -> {
                    List<Place> out = new ArrayList<>();
                    for (AutocompletePrediction p : resp.getAutocompletePredictions()) {
                        out.add(new Place(p.getPlaceId(), p.getPrimaryText(null).toString(),
                                p.getSecondaryText(null).toString(), Double.NaN, Double.NaN));
                    }
                    cb.onSuccess(out);
                })
                .addOnFailureListener(e -> cb.onError(map(e)));
    }

    @Override
    public void getPlace(String placeId, Callback<Place> cb) {
        List<com.google.android.libraries.places.api.model.Place.Field> fields = Arrays.asList(
                com.google.android.libraries.places.api.model.Place.Field.ID,
                com.google.android.libraries.places.api.model.Place.Field.NAME,
                com.google.android.libraries.places.api.model.Place.Field.ADDRESS,
                com.google.android.libraries.places.api.model.Place.Field.LAT_LNG);
        FetchPlaceRequest req = FetchPlaceRequest.builder(placeId, fields).setSessionToken(token).build();
        client.fetchPlace(req)
                .addOnSuccessListener(resp -> {
                    token = AutocompleteSessionToken.newInstance();
                    com.google.android.libraries.places.api.model.Place g = resp.getPlace();
                    LatLng ll = g.getLatLng();
                    if (ll == null) {
                        cb.onError(new AppException(ErrorCode.NOT_FOUND,
                                "Location not found. Please try a different search."));
                        return;
                    }
                    cb.onSuccess(new Place(g.getId(), g.getName(), g.getAddress(), ll.latitude, ll.longitude));
                })
                .addOnFailureListener(e -> cb.onError(map(e)));
    }

    private static AppException map(Exception e) {
        SafeLogger.log("Places error: " + e);
        if (e instanceof ApiException && ((ApiException) e).getStatusCode() == 7) {
            return new AppException(ErrorCode.OFFLINE,
                    "Couldn't reach the location service. Check your connection and try again.", e);
        }
        return new AppException(ErrorCode.UNKNOWN, "The location service returned an error.", e);
    }
}
