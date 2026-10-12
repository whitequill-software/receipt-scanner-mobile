package com.example.byok.net

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.URI
import java.util.concurrent.TimeUnit

object SecureHttpClient {

    /**
     * Redirect interceptor that detects cross-origin redirections and strips
     * sensitive authentication credentials before following them.
     */
    private class CrossOriginCredentialStripInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val response = chain.proceed(originalRequest)

            if (response.isRedirect) {
                val locationHeader = response.header("Location")
                if (!locationHeader.isNullOrBlank()) {
                    try {
                        val originalUri = originalRequest.url.toUri()
                        val targetUri = URI.create(locationHeader)
                        val isCrossOrigin = targetUri.isAbsolute &&
                                (!targetUri.host.equals(originalUri.host, ignoreCase = true) ||
                                        targetUri.port != originalUri.port ||
                                        !targetUri.scheme.equals(originalUri.scheme, ignoreCase = true))

                        if (isCrossOrigin) {
                            // Strip authentication headers on cross-origin redirect
                            val strippedRequest = originalRequest.newBuilder()
                                .url(locationHeader)
                                .removeHeader("Authorization")
                                .removeHeader("x-goog-api-key")
                                .removeHeader("x-api-key")
                                .build()
                            response.close()
                            return chain.proceed(strippedRequest)
                        }
                    } catch (_: Exception) {
                        // If URI parsing fails, do not proceed with sensitive headers
                    }
                }
            }
            return response
        }
    }

    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(CrossOriginCredentialStripInterceptor())
            .build()
    }
}
