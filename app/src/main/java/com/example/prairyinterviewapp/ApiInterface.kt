package com.example.prairyinterviewapp

import io.reactivex.Observable
import retrofit2.http.GET

interface ApiInterface {

    // This is where the Parser/Retrofit will get the specific URL and implements the methods to create
    // so we never actually define the interface ourselves. It does that for us.
    @GET("resource/j8a4-a59k.json")
    fun getRestaurants() : Observable<List<Restaurant>>

    //Did return Call<List<Restaurant>> before implementing RxJava into retrofit builder
}