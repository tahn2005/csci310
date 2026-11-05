package com.team23.trojanrideshare.data.local;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.util.SafeLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * A small document database kept in one JSON file on the device. Documents have the same
 * shape as in Cloud Firestore. All work runs on one background thread; each operation either
 * completes and is saved to disk, or fails and leaves the saved data unchanged.
 */
public final class LocalStore {

    /** One unit of work against the store; throwing discards every change it made. */
    public interface Work<T> {
        T run(LocalStore store) throws AppException;
    }

    private static final String ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final File file;
    private final Executor io;
    private final Executor main;
    private final SecureRandom random = new SecureRandom();
    /** collection -> document id -> document. Touched only on the io thread. */
    private Map<String, Map<String, Map<String, Object>>> data = new LinkedHashMap<>();
    private boolean loaded;
    private boolean dirty;

    public LocalStore(File file, Executor io, Executor main) {
        this.file = file;
        this.io = io;
        this.main = main;
    }

    /** Runs {@code work} on the store's thread and reports the result on the main thread. */
    public <T> void run(Work<T> work, Callback<T> cb) {
        io.execute(() -> {
            T result;
            try {
                if (!loaded) load();
                result = work.run(this);
                if (dirty) save();
            } catch (AppException e) {
                reloadAfterFailure();
                main.execute(() -> cb.onError(e));
                return;
            } catch (RuntimeException | IOException | JSONException e) {
                SafeLogger.log("Local database error: " + e);
                reloadAfterFailure();
                AppException err = new AppException(ErrorCode.UNKNOWN,
                        "Couldn't save on this device. Please try again.", e);
                main.execute(() -> cb.onError(err));
                return;
            }
            main.execute(() -> cb.onSuccess(result));
        });
    }

    // ------------------------------------------------------------------ document access (io thread)

    /** A copy of the document, or null. */
    public Map<String, Object> get(String collection, String id) {
        Map<String, Object> doc = collection(collection).get(id);
        return doc == null ? null : copy(doc);
    }

    public void put(String collection, String id, Map<String, Object> doc) {
        collection(collection).put(id, copy(doc));
        dirty = true;
    }

    public void delete(String collection, String id) {
        if (collection(collection).remove(id) != null) dirty = true;
    }

    /** Copies of every document in a collection, keyed by id, in insertion order. */
    public Map<String, Map<String, Object>> all(String collection) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : collection(collection).entrySet()) {
            out.put(e.getKey(), copy(e.getValue()));
        }
        return out;
    }

    /** A random 20-character id, like Firestore's. */
    public String newId() {
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
        return sb.toString();
    }

    private Map<String, Map<String, Object>> collection(String name) {
        return data.computeIfAbsent(name, k -> new LinkedHashMap<>());
    }

    // ------------------------------------------------------------------ persistence

    private void load() throws IOException, JSONException {
        data = new LinkedHashMap<>();
        if (file.exists()) {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(json);
            Iterator<String> names = root.keys();
            while (names.hasNext()) {
                String name = names.next();
                JSONObject docs = root.getJSONObject(name);
                Map<String, Map<String, Object>> col = collection(name);
                Iterator<String> ids = docs.keys();
                while (ids.hasNext()) {
                    String id = ids.next();
                    col.put(id, toMap(docs.getJSONObject(id)));
                }
            }
        }
        loaded = true;
        dirty = false;
    }

    /** Writes a temporary file and renames it, so a crash never leaves a half-written file. */
    private void save() throws IOException, JSONException {
        JSONObject root = new JSONObject();
        for (Map.Entry<String, Map<String, Map<String, Object>>> col : data.entrySet()) {
            JSONObject docs = new JSONObject();
            for (Map.Entry<String, Map<String, Object>> doc : col.getValue().entrySet()) {
                docs.put(doc.getKey(), toJson(doc.getValue()));
            }
            root.put(col.getKey(), docs);
        }
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Can't create " + parent);
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(file)) throw new IOException("Can't replace " + file);
        dirty = false;
    }

    private void reloadAfterFailure() {
        try {
            load();
        } catch (IOException | JSONException e) {
            SafeLogger.log("Local database reload failed: " + e);
            loaded = false;
        }
    }

    // ------------------------------------------------------------------ JSON <-> plain values

    private static Object toJson(Object v) throws JSONException {
        if (v == null) return JSONObject.NULL;
        if (v instanceof Map) {
            JSONObject o = new JSONObject();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) o.put(String.valueOf(e.getKey()), toJson(e.getValue()));
            return o;
        }
        if (v instanceof List) {
            JSONArray a = new JSONArray();
            for (Object x : (List<?>) v) a.put(toJson(x));
            return a;
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? JSONObject.NULL : d;
        }
        return v;
    }

    private static Map<String, Object> toMap(JSONObject o) throws JSONException {
        Map<String, Object> m = new LinkedHashMap<>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            m.put(k, fromJson(o.get(k)));
        }
        return m;
    }

    private static Object fromJson(Object v) throws JSONException {
        if (v == null || v == JSONObject.NULL) return null;
        if (v instanceof JSONObject) return toMap((JSONObject) v);
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) out.add(fromJson(a.get(i)));
            return out;
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copy(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) out.put(e.getKey(), copyValue(e.getValue()));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object v) {
        if (v instanceof Map) return copy((Map<String, Object>) v);
        if (v instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object x : (List<Object>) v) out.add(copyValue(x));
            return out;
        }
        return v;
    }
}
