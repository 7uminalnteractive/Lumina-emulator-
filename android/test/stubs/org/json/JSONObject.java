package org.json;

import java.util.LinkedHashMap;
import java.util.Map;

/** SÓ PARA TESTE no computador. Imita a semântica do org.json do ANDROID, inclusive
 *  a pegadinha: optString() de um null literal devolve a STRING "null". */
public class JSONObject {
    public static final Object NULL = new Object() { @Override public String toString() { return "null"; } };
    final Map<String, Object> map = new LinkedHashMap<>();

    public JSONObject() {}
    public JSONObject(String json) throws JSONException {
        Object v = new MiniParser(json).parseTop();
        if (!(v instanceof JSONObject)) throw new JSONException("não é objeto");
        map.putAll(((JSONObject) v).map);
    }
    public Object opt(String k) { return map.get(k); }
    public boolean isNull(String k) { Object v = map.get(k); return v == null || v == NULL; }
    public String optString(String k, String fallback) {
        Object v = map.get(k);
        return v == null ? fallback : (v instanceof String ? (String) v : String.valueOf(v));
    }
    public long optLong(String k, long fallback) {
        Object v = map.get(k);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) { try { return Long.parseLong((String) v); } catch (NumberFormatException e) { return fallback; } }
        return fallback;
    }
    public JSONArray optJSONArray(String k) { Object v = map.get(k); return v instanceof JSONArray ? (JSONArray) v : null; }
    public JSONObject optJSONObject(String k) { Object v = map.get(k); return v instanceof JSONObject ? (JSONObject) v : null; }
    @Override public String toString() { return map.toString(); }
}
