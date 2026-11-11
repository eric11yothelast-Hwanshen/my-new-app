package com.hmoney.app.data.ai

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * OpenAI 相容 Chat Completions API。
 * Base URL／金鑰／模型皆由使用者自填（Mistral、自建 proxy 皆可），
 * 因此以 @Url 傳入完整端點、以 Header 帶入金鑰。
 */
interface AiApi {

    @POST
    suspend fun chat(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: RequestBody,
    ): Response<okhttp3.ResponseBody>
}
