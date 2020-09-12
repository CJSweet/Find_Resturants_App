package com.example.prairyinterviewapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.GsonBuilder
import okhttp3.*
import java.io.IOException
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    // for requesting the permission to get fine location
    companion object {
        private const val PERMISSION_LOCATION_CODE = 101
    }

    // For the Map fragment
    // https://www.youtube.com/watch?v=suwq7Nta3oM
    lateinit var mapFragment: SupportMapFragment

    // This is for finding the User location through Google Play Services
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var myLoc: LatLng
    private lateinit var coordList: ArrayList<LatLng>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // init coordList for later, stores the LatLng of all restuarants that will be shown
        coordList = ArrayList()

        // init Map Fragment
        mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment

        // location permission check, if approved, get user information
        // then get API info, then show Map with User and resturant locations
        checkLocationPermission()
    }


    private fun checkLocationPermission() {
        // If permission has not yet been approved, ask for approval
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_DENIED
        ) {
            val permission = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            requestPermissions(permission, PERMISSION_LOCATION_CODE)
        } else {
            // if permission already accepted, then get User Location
            getUserLocation()
        }
    }

    // After user responds to permission dialog
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {

            PERMISSION_LOCATION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // User has selected the permission to be granted
                    getUserLocation()
                } else {
                    // If user denies the request, show Toast saying location must be approved for this app
                    Toast.makeText(
                        this,
                        "Must grant location permission to use this app",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun getAPIInfo() {
        // Most of the code in this method is from: // https://www.youtube.com/watch?v=53BsyxwSBJk&t=762s

        //Specify URL
        val url = "https://data.cityofchicago.org/resource/j8a4-a59k.json"

        // Need a request built to send for getting the JSON file
        val request = Request.Builder().url(url).build()

        // create the client to request the JSON file from site
        val client = OkHttpClient()

        //enqueue needs response Callback, and this runs on another thread, not main
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("MainActivity", "onFailure: Request for JSON has failed")
            }

            // If request is  Success full, get body of response, and convert to the needed LatLng
            override fun onResponse(call: Call, response: Response) {
                // get body
                val body = response.body?.string()

                // Gson is good for making JSON into Java objects and vice versa
                val gson = GsonBuilder().create()

                // Make a list of the body response, since the data comes in an array like format
                // code from: https://stackoverflow.com/questions/45603236/using-gson-in-kotlin-to-parse-json-array
                // Only want the first 100 entries, because those are the most recent
                val dataAsList =
                    gson.fromJson(body, Array<Restaurant>::class.java).asList().subList(0, 100)

                // filter the Data list to remove all Risk 3 restaurants, leaving only Risk 2 and Risk 1 resturants
                val filteredData = dataAsList.filter {
                    it.risk != "Risk 3 (Low)"
                }

                // with the filtered list, make a list of LatLng variables of each restuarant so we can use it for adding
                // markers to the map
                for (place in filteredData.indices) {
                    coordList.add(
                        LatLng(
                            filteredData[place].latitude.toDouble(),
                            filteredData[place].longitude.toDouble()
                        )
                    )
                }

                // Now that the user Location has been found and the locations
                // of the resturants are ready, we can now set the Map up
                // Must be in the Main Thread so we need to switch back over from this thread.
                GlobalScope.launch(Dispatchers.Main) {
                    setMap()
                }
            }
        })
    }

    // The only way to reach this method is if permission already has been granted, so we can
    // suppress the error message
    @SuppressLint("MissingPermission")
    private fun getUserLocation() {
        //create Fused Location Provider Client to begin getting last know location
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // get last location, which most likely is where the user is located
        // there are rare exceptions, but for this app I will not worry about those instances
        fusedLocationProviderClient.lastLocation.addOnSuccessListener {
            if (it != null) {
                Log.d(
                    "MainActivity",
                    "onCreate: Location: lat: ${it.latitude} long: ${it.longitude}"
                )
                // make LatLng of user location
                myLoc = LatLng(it.latitude, it.longitude)

                // once user location is found, we still need API info before setting up the map
                getAPIInfo()
            }
        }
    }

    // The only way to reach this method is if permission already has been granted, so we can
    // suppress the error message
    @SuppressLint("MissingPermission")
    private fun setMap() {
        // we now have all the information needed to create or map
        mapFragment.getMapAsync {
            //  allow for location of user to be shown
            it.isMyLocationEnabled = true
            // change the camera position to be where the user is (in this instance, Chicago)
            // and make the Zoom level 10 so streets can be seen but all restaurant markers are also
            // visible
            it.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition(
                        myLoc,
                        10F,
                        0F,
                        0F
                    )
                )
            )

            // finally, we need to add the markers, for each element in coordList,
            // create a marker at that LatLng
            for (latLng in coordList.indices) {
                it.addMarker(MarkerOptions().position(coordList[latLng]))
            }
        }
    }
}

// This class is used when getting the APIInfo
// Gson converts each JSON array element into this
// Restaurant object
class Restaurant(
    val risk: String,
    val latitude: String,
    val longitude: String
)


// I tried to work with RxJava and RetroFit to get the Json file and elements
// since that is the popular method but I could not figure it out. It would be better
// than using OkHttp by itself because with RxJava and Retrofit I believe it would not
// download the entire JSON file and take up so much storage. However, I could not get it
// to work and using OkHttp and Gson was much more understandable for me.
// Below is some of the code I was trying to work with

//        val string = Retrofit.Builder()
//            .baseUrl("https://data.cityofchicago.org/")
//            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//            .create(Api::class.java)
//            .loadDataInUsualWay()

//data class DataHolder (
//    @SerializedName("data") val data: Array<DataItem>
//)
//
//data class DataItem (
//    @SerializedName("inspection_id") val inspectionId: Long,
//    @SerializedName("inspection_date") val inspectionDate: String,
//    @SerializedName("risk") val risk : String,
//    @SerializedName("latitude") val latitude : Int,
//    @SerializedName("longitude") val longitude : Int
//)
//
//interface Api {
//
//    @GET("resource/j8a4-a59k.json")
//    fun loadDataInUsualWay(): Single<DataHolder>
//}

