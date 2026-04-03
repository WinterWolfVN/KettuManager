package dev.beefers.vendetta.manager.network.service

import dev.beefers.vendetta.manager.network.utils.ApiError
import dev.beefers.vendetta.manager.network.utils.ApiFailure
import dev.beefers.vendetta.manager.network.utils.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class HttpService(
    val json: Json,
    val http: HttpClient
) {

    suspend inline fun <reified T> request(builder: HttpRequestBuilder.() -> Unit = {}): ApiResponse<T> {
        return try {
            val response = http.request(builder)

            if (response.status.isSuccess()) {
                val result = response.body<T>()
                ApiResponse.Success(result)
            } else {
                ApiResponse.Error(ApiError(response.status, null))
            }
        } catch (e: Throwable) {
            ApiResponse.Failure(ApiFailure(e, null))
        }
    }

}
