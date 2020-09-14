package com.example.prairyinterviewapp

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory


// Singleton Object for creating and linking the retrofit builder with the interface
object ServiceBuilder {

    //Specify base URL
    private val baseUrl = "https://data.cityofchicago.org/"

    // Build the OkHttp client first, so that the retrofit builder has
    // a client to make the call
    private val client = OkHttpClient.Builder().build()

    // create the retrofit builder
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create()) // create an RxJava so we can have observable when making the call
        .addConverterFactory(GsonConverterFactory.create()) //this says we would like to convert the data using Gson
        .client(client)
        .build()
        .create(ApiInterface::class.java)

    // This function will be called to connect the retrofit builder with the defined interface
    // so a complete retrofit call can be made. And the interface can have its methods defined
    fun buildService(): ApiInterface {
        return retrofit
    }
}