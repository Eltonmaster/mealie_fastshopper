package io.github.eltonmaster.fastshopper.data.api

import io.github.eltonmaster.fastshopper.BuildConfig
import io.github.eltonmaster.fastshopper.data.ServerUrl
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object MealieClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun create(serverUrl: String, token: String): MealieApi {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(ServerUrl.asBaseUrl(serverUrl))
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MealieApi::class.java)
    }
}
