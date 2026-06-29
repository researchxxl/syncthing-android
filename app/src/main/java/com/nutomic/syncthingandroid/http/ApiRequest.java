package com.nutomic.syncthingandroid.http;


import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.Nullable;
import android.util.Log;
import android.widget.ImageView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public abstract class ApiRequest {

    private static final String TAG = "ApiRequest";

    private Boolean ENABLE_VERBOSE_LOG = false;

    /**
     * The name of the HTTP header used for the syncthing API key.
     */
    private static final String HEADER_API_KEY = "X-API-Key";

    public interface OnSuccessListener {
        void onSuccess(String result);
    }

    public interface OnImageSuccessListener {
        void onImageSuccess(Bitmap result);
    }

    public interface OnErrorListener {
        void onError(VolleyError error);
    }

    private static RequestQueue sVolleyQueue;

    private RequestQueue getVolleyQueue() {
        if (sVolleyQueue == null) {
            Context context = mContext.getApplicationContext();
            sVolleyQueue = Volley.newRequestQueue(context, new NetworkStack());
        }
        return sVolleyQueue;
    }

    private final Context mContext;
    private final URL mUrl;
    private final String mPath;
    private final String mApiKey;

    ApiRequest(Context context, URL url, String path, String apiKey) {
        mContext = context;
        // The app only ever talks to the local syncthing instance. Pin the connection to the
        // loopback interface regardless of the configured GUI listen address (which is 0.0.0.0
        // when remote access is enabled), keeping the API key and config off any routable interface.
        mUrl           = forceLoopbackHost(url);
        mPath          = path;
        mApiKey        = apiKey;
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(context);
    }

    /**
     * Rewrites the host of the given URL to 127.0.0.1, preserving the scheme and port. The port
     * comes from the configured GUI listen address; falls back to the default web GUI port.
     *
     * <p>Forcing loopback is intentional and security-relevant, not merely a convenience:
     * <ul>
     *   <li>The app only ever sets the GUI address to {@code 127.0.0.1} or {@code 0.0.0.0} (the
     *       "listen on all interfaces" setting). {@code 0.0.0.0} always includes loopback, so the
     *       local instance is reachable on {@code 127.0.0.1} in every app-managed config.</li>
     *   <li>It keeps the API key and configuration off any routable interface.</li>
     *   <li>It is the precondition that makes the two TLS relaxations safe: disabling hostname
     *       verification ({@link NetworkStack#createConnection}) and falling back to the OS trust
     *       store / user-installed CAs ({@link SyncthingTrustManager}). On loopback there is no
     *       network position for a MITM to occupy, so neither relaxation can be abused.</li>
     * </ul>
     * Do not "simplify" this by connecting to the configured address directly: {@code 0.0.0.0} is
     * not a valid destination (and modern WebView blocks it), and targeting a routable address
     * would break the trust model above.
     */
    private static URL forceLoopbackHost(URL url) {
        try {
            int port = url.getPort() != -1 ? url.getPort() : Constants.DEFAULT_WEBGUI_TCP_PORT;
            return new URL(url.getProtocol(), "127.0.0.1", port, url.getFile());
        } catch (MalformedURLException e) {
            Log.w(TAG, "forceLoopbackHost: Failed to rewrite host, using original URL", e);
            return url;
        }
    }

    Uri buildUri(Map<String, String> params) {
        Uri.Builder uriBuilder = Uri.parse(mUrl.toString())
                .buildUpon()
                .path(mPath);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            uriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        return uriBuilder.build();
    }

    /**
     * Opens the connection, then returns success status and response string.
     */
    void connect(int requestMethod, Uri uri, @Nullable String requestBody,
                 @Nullable OnSuccessListener listener, @Nullable OnErrorListener errorListener) {
        /*
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, "Performing request to " + uri.toString());
        }
        */
        StringRequest request = new StringRequest(requestMethod, uri.toString(), reply -> {
            if (listener != null) {
                listener.onSuccess(reply);
            }
        }, error -> {
            if (errorListener != null) {
                errorListener.onError(error);
            } else {
                int statusCode = 0;
                if (error.networkResponse != null) {
                    statusCode = error.networkResponse.statusCode;
                }
                Log.w(TAG, "Request to " + uri + " failed, code=" + statusCode + ", msg=" + error.getMessage());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return ImmutableMap.of(HEADER_API_KEY, mApiKey);
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return Optional.fromNullable(requestBody).transform(String::getBytes).orNull();
            }

            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                Charset charset = StandardCharsets.ISO_8859_1; // Volley default
                Map<String, String> headers = response.headers;

                if (headers != null) {
                    // explicit charset
                    String parsedCharset = HttpHeaderParser.parseCharset(headers, null);
                    if (parsedCharset != null) {
                        charset = Charset.forName(parsedCharset);
                    }
                    // application/json without charset → UTF-8
                    else {
                        String contentType = headers.get("Content-Type");
                        if (contentType != null &&
                                contentType.toLowerCase(Locale.US).startsWith("application/json")) {
                            charset = StandardCharsets.UTF_8;
                        }
                    }
                }

                String parsed = new String(response.data, charset);
                return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
            }
        };

        // Some requests seem to be slow or fail, make sure this doesn't break the app
        // (eg if an event request fails, new event requests won't be triggered).
        request.setRetryPolicy(new DefaultRetryPolicy(5000, 5,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        getVolleyQueue().add(request);
    }

    /**
     * Opens the connection, then returns success status and response bitmap.
     */
    void makeImageRequest(Uri uri, @Nullable OnImageSuccessListener imageListener,
                          @Nullable OnErrorListener errorListener) {
        ImageRequest imageRequest =  new ImageRequest(uri.toString(), bitmap -> {
            if (imageListener != null) {
                imageListener.onImageSuccess(bitmap);
            }
        }, 0, 0, ImageView.ScaleType.CENTER, Bitmap.Config.RGB_565, volleyError -> {
            if(errorListener != null) {
                errorListener.onError(volleyError);
            }
            Log.d(TAG, "onErrorResponse: " + volleyError);
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return ImmutableMap.of(HEADER_API_KEY, mApiKey);
            }
        };

        getVolleyQueue().add(imageRequest);
    }

    /**
     * Extends {@link HurlStack}, uses {@link #getSslSocketFactory()} and disables hostname
     * verification.
     */
    private class NetworkStack extends HurlStack {

        public NetworkStack() {
            super(null, getSslSocketFactory());
        }
        @Override
        protected HttpURLConnection createConnection(URL url) throws IOException {
            if (mUrl.toString().startsWith("https://")) {
                HttpsURLConnection connection = (HttpsURLConnection) super.createConnection(url);
                // Safe to skip hostname verification: the connection is pinned to the loopback
                // interface (see forceLoopbackHost), so there is no network MITM surface and the
                // certificate's SAN/CN need not match 127.0.0.1 (a user-supplied CA cert is
                // typically issued for a real hostname, not the loopback address). Trust is still
                // enforced by SyncthingTrustManager: the self-signed pin first, then the OS trust
                // store.
                connection.setHostnameVerifier((hostname, session) -> true);
                return connection;
            }
            return super.createConnection(url);
        }
    }

    private SSLSocketFactory getSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            File httpsCertPath = Constants.getHttpsCertFile(mContext);
            sslContext.init(null, new TrustManager[]{new SyncthingTrustManager(httpsCertPath)},
                    new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            Log.w(TAG, e);
            return null;
        }
    }
}
