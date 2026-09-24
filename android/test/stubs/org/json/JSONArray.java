package org.json;

import java.util.ArrayList;
import java.util.List;

public class JSONArray {
    final List<Object> list = new ArrayList<>();
    public int length() { return list.size(); }
    public boolean isNull(int i) { Object v = i >= 0 && i < list.size() ? list.get(i) : null; return v == null || v == JSONObject.NULL; }
    public String optString(int i, String fallback) {
        Object v = i >= 0 && i < list.size() ? list.get(i) : null;
        return v == null ? fallback : (v instanceof String ? (String) v : String.valueOf(v));
    }
    public JSONObject optJSONObject(int i) { Object v = i >= 0 && i < list.size() ? list.get(i) : null; return v instanceof JSONObject ? (JSONObject) v : null; }
    public JSONObject getJSONObject(int i) throws JSONException {
        Object v = i >= 0 && i < list.size() ? list.get(i) : null;
        if (!(v instanceof JSONObject)) throw new JSONException("índice " + i + " não é objeto");
        return (JSONObject) v;
    }
}
