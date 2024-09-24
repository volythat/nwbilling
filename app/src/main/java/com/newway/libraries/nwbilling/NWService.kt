package com.newway.libraries.nwbilling

import com.google.gson.Gson
import com.newway.libraries.nwbilling.model.NWPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit


interface NWServiceApi {
    companion object {

        operator fun invoke(): NWServiceApi {

            val builder = OkHttpClient.Builder()
                .readTimeout(50, TimeUnit.SECONDS)
                .writeTimeout(50, TimeUnit.SECONDS)

            val client = builder.build()
            return Retrofit.Builder()
                .client(client)
                .baseUrl("http://34.46.96.2:8000/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NWServiceApi::class.java)
        }
    }

    @FormUrlEncoded
    @POST("api/verify-android-subscription")
    suspend fun publisher(@Field("packageName") packageName: String,
                          @Field("subscriptionId") subscriptionId: String,
                          @Field("token") token: String,
                          @Field("signature") signature: String) : Response<NWPublisher?>
}
class NWApiResponse(private val api: NWServiceApi) {
    private fun handleResult(result: Response<NWPublisher?>): NWPublisher? {
        return if (result.isSuccessful) {
            result.body()
        } else {
            null
        }
    }

    // Api

    fun publisher(packageName:String,subscriptionId:String,token:String,signature:String) = flow {
        val result = api.publisher(packageName,subscriptionId,token,signature)
        emit(result.body())
    }.catch {
        emit(null)
    }.flowOn(Dispatchers.IO)

}