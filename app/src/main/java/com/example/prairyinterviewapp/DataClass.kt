package com.example.prairyinterviewapp

// This class is used when getting the APIInfo
// Gson converts each JSON array element into this
// Restaurant object
data class Restaurant(
    val risk: String,
    val latitude: String,
    val longitude: String
    // if you wanted to name the variable different than what the variable is names in the JSON
    // then you must use this annotation:
    //
    // @SerializedName("JSON variable Name here")
    // val yourKotlinObjectHere
    //
    // This way, the JSON Parser know that the JSON variable needs to become your Kotlin Object
)