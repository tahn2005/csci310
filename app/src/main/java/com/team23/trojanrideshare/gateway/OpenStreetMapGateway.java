package com.team23.trojanrideshare.gateway;

import android.os.Handler;
import android.os.Looper;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.util.SafeLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Location API backed by OpenStreetMap data through the free Photon geocoder
 * (https://photon.komoot.io). Needs no API key. Results are biased toward USC.
 */
public class OpenStreetMapGateway implements LocationGateway {

    private static final String ENDPOINT = "https://photon.komoot.io/api/";
    private static final double USC_LAT = 34.0224;
    private static final double USC_LNG = -118.2851;
    private static final int LIMIT = 6;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Place> cache = new ConcurrentHashMap<>();

    @Override
    public void suggest(String query, Callback<List<Place>> cb) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            cb.onSuccess(new ArrayList<>());
            return;
        }
        io.execute(() -> {
            try {
                String url = ENDPOINT + "?q=" + URLEncoder.encode(q, "UTF-8")
                        + "&limit=" + LIMIT + "&lang=en&lat=" + USC_LAT + "&lon=" + USC_LNG;
                List<Place> places = parse(get(url));
                for (Place p : places) cache.put(p.getPlaceId(), p);
                main.post(() -> cb.onSuccess(places));
            } catch (Exception e) {
                SafeLogger.log("Location lookup failed: " + e);
                main.post(() -> cb.onError(new AppException(ErrorCode.OFFLINE,
                        "Couldn't reach the location service. Check your connection and try again.", e)));
            }
        });
    }

    @Override
    public void getPlace(String placeId, Callback<Place> cb) {
        Place p = cache.get(placeId);
        if (p != null) {
            cb.onSuccess(p);
        } else {
            cb.onError(new AppException(ErrorCode.NOT_FOUND, "Location not found. Please try a different search."));
        }
    }

    private static String get(String spec) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(spec).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "TrojanRideshare/1.0 (USC CSCI 310 student project)");
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    private static List<Place> parse(String json) throws Exception {
        List<Place> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        JSONArray features = new JSONObject(json).optJSONArray("features");
        if (features == null) return out;
        for (int i = 0; i < features.length(); i++) {
            JSONObject f = features.getJSONObject(i);
            JSONObject props = f.optJSONObject("properties");
            JSONObject geom = f.optJSONObject("geometry");
            if (props == null || geom == null) continue;
            JSONArray coords = geom.optJSONArray("coordinates");
            if (coords == null || coords.length() < 2) continue;

            String street = props.optString("street", "");
            String house = props.optString("housenumber", "");
            String name = props.optString("name", "");
            if (name.isEmpty()) name = (house + " " + street).trim();
            if (name.isEmpty()) name = props.optString("city", "");
            if (name.isEmpty()) continue;

            List<String> parts = new ArrayList<>();
            if (!street.isEmpty() && !name.contains(street)) {
                parts.add((house + " " + street).trim());
            }
            String city = props.optString("city", "");
            if (!city.isEmpty() && !city.equals(name)) parts.add(city);
            String state = props.optString("state", "");
            if (!state.isEmpty() && !state.equals(name)) parts.add(state);
            String address = String.join(", ", parts);

            String id = "osm:" + props.optString("osm_type", "X") + props.optLong("osm_id", i);
            Place p = new Place(id, name, address, coords.getDouble(1), coords.getDouble(0));
            if (seen.add(p.label())) out.add(p);
        }
        return out;
    }
}
