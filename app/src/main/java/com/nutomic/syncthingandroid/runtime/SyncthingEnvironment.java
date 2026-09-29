package com.nutomic.syncthingandroid.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable environment selected for a bundled Syncthing invocation.
 *
 * <p>The named builder methods keep the normal execution contract visible while allowing a
 * future backend to transport the same values without rebuilding them.</p>
 */
public final class SyncthingEnvironment {
    private final Map<String, String> values;

    private SyncthingEnvironment(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, String> values() {
        return values;
    }

    public static final class Builder {
        private final Map<String, String> values = new LinkedHashMap<>();

        public Builder home(String value) {
            values.put("HOME", Objects.requireNonNull(value));
            return this;
        }

        public Builder syncthingHome(String value) {
            values.put("STHOMEDIR", Objects.requireNonNull(value));
            return this;
        }

        public Builder trace(String value) {
            values.put("STTRACE", Objects.requireNonNull(value));
            return this;
        }

        public Builder monitored() {
            values.put("STMONITORED", "1");
            return this;
        }

        public Builder noUpgrade() {
            values.put("STNOUPGRADE", "1");
            return this;
        }

        public Builder versionExtra(String value) {
            values.put("STVERSIONEXTRA", Objects.requireNonNull(value));
            return this;
        }

        public Builder sqliteTemporaryDirectory(String value) {
            values.put("SQLITE_TMPDIR", Objects.requireNonNull(value));
            return this;
        }

        public Builder fallbackGatewayIpv4(String value) {
            if (value != null) {
                values.put("FALLBACK_NET_GATEWAY_IPV4", value);
            }
            return this;
        }

        public Builder torProxy() {
            values.put("all_proxy", "socks5://localhost:9050");
            values.put("ALL_PROXY_NO_FALLBACK", "1");
            return this;
        }

        public Builder socksProxy(String value) {
            if (value != null && !value.isEmpty()) {
                values.put("all_proxy", value);
            }
            return this;
        }

        public Builder httpProxy(String value) {
            if (value != null && !value.isEmpty()) {
                values.put("http_proxy", value);
                values.put("https_proxy", value);
            }
            return this;
        }

        public Builder gogc(int value) {
            values.put("GOGC", Integer.toString(value));
            return this;
        }

        public Builder customVariables(Map<String, String> customVariables) {
            if (customVariables != null) {
                for (Map.Entry<String, String> entry : customVariables.entrySet()) {
                    values.put(
                            Objects.requireNonNull(entry.getKey()),
                            Objects.requireNonNull(entry.getValue())
                    );
                }
            }
            return this;
        }

        public SyncthingEnvironment build() {
            return new SyncthingEnvironment(values);
        }
    }
}
