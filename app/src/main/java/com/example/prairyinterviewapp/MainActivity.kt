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
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // for requesting the permission to get fine location
    companion object {
        private const val PERMISSION_LOCATION_CODE = 101
    }

    // For the Map fragment
    // https://www.youtube.com/watch?v=suwq7Nta3oM
    private lateinit var mapFragment: SupportMapFragment

    // Location of phone is a global variable because it is needed
    // in two methods (getUserLocation, and setMap) that are not
    // run adjacent to each other
    private lateinit var myLoc: LatLng

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // init Map Fragment
        mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment

        // location permission check, if approved, get user information
        // then get API info, then show Map with User and restaurant locations
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

    // The only way to reach this method is if permission already has been granted, so we can
    // suppress the error message
    @SuppressLint("MissingPermission")
    private fun getUserLocation() {
        // create Fused Location Provider Client to begin getting last know location
        val fusedLocationProviderClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)

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

    private fun getAPIInfo() {
        // Most of the code in this method and from the ServiceBuilder/DataClass/ApiInterface files is from:
        // https://dev.to/paulodhiambo/kotlin-and-retrofit-network-calls-2353#:~:text=use%20retrofit%20to%20make%20network%20calls%20from%20our%20applications.&text=Http%20client%20fro%20android%20created%20by%20the%20same%20company%20that%20created%20retrofit.&text=into%20a%20JSON%20format.
        // and RxJava code from:
        // https://dev.to/paulodhiambo/kotlin-rxjava-retrofit-tutorial-18hn

        // Composite Disposable combines all disposables so it is easier to remove all
        // observers when done observing
        //
        // Disposables are the connections between the observable and the observer
        // When observable is created, a "stream" runs from the observable to the observer,
        // This stream is called a Disposable
        val compositeDisposable = CompositeDisposable()

        compositeDisposable.add(
            ServiceBuilder.buildService() // call function from object
                .getRestaurants() // call method from interface to request JSON from URL with GET request
                .observeOn(Schedulers.io()) // observe the results on the main thread
                .subscribeOn(Schedulers.io()) // work on a background io thread
                .subscribe(
                    { response -> onResponse(response) },
                    { error -> onError(error) }) // these will be the subscribe functions that will be called on completion
        )
    }

    private fun onResponse(restaurants: List<Restaurant>) {
        // get body List of all restaurants, get first 100 and then filter it
        val filteredData = restaurants.subList(0, 100).filter {
            it.risk != "Risk 3 (Low)"
        }

        // ArrayList for the LatLng's needed for restaurant markers
        // Coordinates List
        val coordList = ArrayList<LatLng>()

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
        // of the restaurants are ready, we can now set the Map up
        // Must be in the Main Thread so we need to switch back over from this thread.
        GlobalScope.launch(Dispatchers.Main) {
            setMap(coordList)
        }
    }

    private fun onError(error: Throwable) {
        Log.d("MainActivity", "onFailure: Request for JSON has failed - $error")

        // Let the user know that something went wrong with the request for the restaurants by showing
        // Toast in main thread
        GlobalScope.launch(Dispatchers.Main) {
            showErrorToast(error)
        }
    }

    private fun showErrorToast(error: Throwable) {
        Toast.makeText(this, "Error with request: $error", Toast.LENGTH_SHORT).show()
    }

    // The only way to reach this method is if permission already has been granted, so we can
    // suppress the error message
    @SuppressLint("MissingPermission")
    private fun setMap(coordList: ArrayList<LatLng>) {
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
