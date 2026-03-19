package com.xiaoai.islandnotify.modernhook;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class XSharedPreferences implements SharedPreferences {

    private final String prefFileName;
    private SharedPreferences delegate;

    public XSharedPreferences(String prefFileName) {
        this.prefFileName = prefFileName;
        reload();
    }

    public void reload() {
        try {
            delegate = XposedBridge.getRemotePreferences(prefFileName);
        } catch (Throwable ignored) {
            delegate = null;
        }
    }

    @Override
    public Map<String, ?> getAll() {
        return delegate != null ? delegate.getAll() : Collections.emptyMap();
    }

    @Override
    public String getString(String key, String defValue) {
        return delegate != null ? delegate.getString(key, defValue) : defValue;
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return delegate != null ? delegate.getStringSet(key, defValues) : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        return delegate != null ? delegate.getInt(key, defValue) : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        return delegate != null ? delegate.getLong(key, defValue) : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        return delegate != null ? delegate.getFloat(key, defValue) : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return delegate != null ? delegate.getBoolean(key, defValue) : defValue;
    }

    @Override
    public boolean contains(String key) {
        return delegate != null && delegate.contains(key);
    }

    @Override
    public Editor edit() {
        throw new UnsupportedOperationException("XSharedPreferences is read-only");
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        if (delegate != null) {
            delegate.registerOnSharedPreferenceChangeListener(listener);
        }
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        if (delegate != null) {
            delegate.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }
}
