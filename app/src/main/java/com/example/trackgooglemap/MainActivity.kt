package com.example.trackgooglemap

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.trackgooglemap.base.ViewModelFactory
import com.example.trackgooglemap.network.ApiService
import com.example.trackgooglemap.network.dto.Coordinates
import com.example.trackgooglemap.utils.AnimationUtils
import com.example.trackgooglemap.utils.MapUtils
import com.example.offlinenews.utils.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), OnMapReadyCallback {


    private lateinit var googleMap: GoogleMap
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var grayPolyline: Polyline? = null
    private var blackPolyline: Polyline? = null
    private var movingCabMarker: Marker? = null
    private var previousLatLng: LatLng? = null
    private var currentLatLng: LatLng? = null
    private var currentTime: String? = null
    private var previousTime: String? = null
    private var isRunning: Boolean = false
    private var snackbar: Snackbar? = null
    private var errorSnackbar: Snackbar? = null

    private var handler: Handler? = null
    private lateinit var thread: HandlerThread
    private lateinit var runnable: Runnable

    private val viewModel: MainViewModel by viewModels{
        ViewModelFactory(apiService = ApiService.invoke())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Подготовка карты
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupObservers()
        setupUI()


    }

    private fun setupUI(){
        ///Make Snackbar
        snackbar = Snackbar.make(constraintView, R.string.loading, Snackbar.LENGTH_INDEFINITE)
        //Default tv value
        speedTextView.text = resources.getString(R.string.speed,0F)
        //Нажатия на fab кнопку
        pauseFab.setOnClickListener {
            isRunning = if(isRunning && handler != null){
                handler?.removeCallbacks(runnable)
                pauseFab.setImageDrawable(resources.getDrawable(R.drawable.ic_baseline_play_arrow_24,theme))
                false
            }else{
                handler?.post(runnable)
                pauseFab.setImageDrawable(resources.getDrawable(R.drawable.ic_baseline_pause_24,theme))
                true
            }
        }
    }


    private fun setupObservers() {
        //Получение координат с сервера
        viewModel.getCoordinates().observe(this, Observer {
            it?.let { resource ->
                when(resource.status){
                    Status.SUCCESS -> {
                        //On Success
                        snackbar?.dismiss()
                        onDataReady(it.data!!)
                    }
                    Status.ERROR -> {
                        Toast.makeText(this, it.message + "ERROR!!!", Toast.LENGTH_SHORT).show()
                        snackbar?.dismiss()
                        errorSnackbar = Snackbar.make(constraintView,R.string.error_message, Snackbar.LENGTH_INDEFINITE)
                                .setAction("Повторить", View.OnClickListener {
                                    setupObservers()
                                    errorSnackbar?.dismiss()
                                })
                        errorSnackbar?.show()

                    }
                    Status.LOADING ->{
                        //On Loading
                        snackbar?.show()
                    }

                }
            }
        })
    }

    private fun onDataReady(data: List<Coordinates>) {
        showPath(data)
        showMovingCab(data)
    }

    private fun animateCamera(latLng: LatLng) {
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(12f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun addCarMarkerAndGet(latLng: LatLng): Marker {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCarBitmap(this))
        return googleMap.addMarker(
                MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor)
        )
    }

    private fun addOriginDestinationMarkerAndGet(latLng: LatLng): Marker {
        val bitmapDescriptor =
                BitmapDescriptorFactory.fromBitmap(MapUtils.getOriginDestinationMarkerBitmap())
        return googleMap.addMarker(
                MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor)
        )
    }


    /**
     * Рисуем маршрут по карте от начала списка до конца
     */
    private fun showPath(coordinatesList: List<Coordinates>) {
        val builder = LatLngBounds.Builder()
        val latLngList = mutableListOf<LatLng>()

        for (coordinate in coordinatesList) {
            latLngList.add(LatLng(coordinate.latitude,coordinate.longitude))
            builder.include(LatLng(coordinate.latitude,coordinate.longitude))
        }

        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 2))

        val polylineOptions = PolylineOptions()
        polylineOptions.color(Color.GRAY)
        polylineOptions.width(5f)
        polylineOptions.addAll(latLngList)
        grayPolyline = googleMap.addPolyline(polylineOptions)

        val blackPolylineOptions = PolylineOptions()
        blackPolylineOptions.color(Color.BLACK)
        blackPolylineOptions.width(5f)
        blackPolyline = googleMap.addPolyline(blackPolylineOptions)

        originMarker = addOriginDestinationMarkerAndGet(latLngList[0])
        originMarker?.setAnchor(0.5f, 0.5f)
        destinationMarker = addOriginDestinationMarkerAndGet(latLngList[latLngList.size - 1])
        destinationMarker?.setAnchor(0.5f, 0.5f)

        val polylineAnimator = AnimationUtils.polylineAnimator()
        polylineAnimator.addUpdateListener { valueAnimator ->
            val percentValue = (valueAnimator.animatedValue as Int)
            val index = (grayPolyline?.points!!.size) * (percentValue / 100.0f).toInt()
            blackPolyline?.points = grayPolyline?.points!!.subList(0, index)
        }
        polylineAnimator.start()
    }

    /**
     * Обновляем позицию машинки
     */
    private  fun updateCarLocation(coordinates: Coordinates) {
        //update tv speed

        val latLng = LatLng(coordinates.latitude,coordinates.longitude)
        //Если еще нет машинки
        if (movingCabMarker == null) {
            movingCabMarker = addCarMarkerAndGet(latLng)
        }
        //Если машинка в начале пути
        if (previousLatLng == null) {
            currentLatLng = latLng
            previousLatLng = currentLatLng

            currentTime = coordinates.time
            previousTime = currentTime

            movingCabMarker?.position = currentLatLng
            movingCabMarker?.setAnchor(0.5f, 0.5f)
            animateCamera(currentLatLng!!)
        } else {
            //Если машинка уже в пути
            previousLatLng = currentLatLng
            currentLatLng = latLng

            previousTime = currentTime
            currentTime = coordinates.time

            val valueAnimator = AnimationUtils.carAnimator()
            valueAnimator.addUpdateListener { va ->
                if (currentLatLng != null && previousLatLng != null) {
                    val multiplier = va.animatedFraction
                    val nextLocation = LatLng(
                            multiplier * currentLatLng!!.latitude + (1 - multiplier) * previousLatLng!!.latitude,
                            multiplier * currentLatLng!!.longitude + (1 - multiplier) * previousLatLng!!.longitude
                    )
                    movingCabMarker?.position = nextLocation
                    val rotation = MapUtils.getRotation(previousLatLng!!, nextLocation)
                    if (!rotation.isNaN()) {
                        movingCabMarker?.rotation = rotation
                    }
                    movingCabMarker?.setAnchor(0.5f, 0.5f)
                }
            }

            valueAnimator.start()

            updateSpeedText(currentLatLng,previousLatLng,currentTime,previousTime)

        }

    }

    //Вычисляеям скорость машинки
    private fun updateSpeedText(
            currentLatLng: LatLng?,
            previousLatLng: LatLng?,
            currentTime: String?,
            previousTime : String?
    ){
        ////Дистанция в метрах
        val distance = MapUtils.getDistance(previousLatLng!!,currentLatLng!!)

        //"yyyy-MM-dd'T'HH:mm:ss"
        val currentTimeFormated = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.getDefault()
        ).parse(currentTime!!)!!

        val previousTimeFormated = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.getDefault()
        ).parse(previousTime!!)!!

        val diff: Long = currentTimeFormated.time -previousTimeFormated.time
        val seconds = diff / 1000

        //метры в секунду
        val currentSpeed = distance/seconds

        if(!currentSpeed.isNaN()){
            //Киллометры в час
            val kmh = currentSpeed * 3.6
            speedTextView.text = resources.getString(R.string.speed,kmh)
        }
    }

    //Запускаем машинку
    private fun showMovingCab(cabLatLngList: List<Coordinates>) {

        var index = 0

        thread = HandlerThread("MyHandlerThread")
        thread.start()

        handler = Handler(thread.looper)
        runnable = Runnable {
            run {
                if (index < cabLatLngList.size) {
                    runOnUiThread {
                        updateCarLocation(cabLatLngList[index])
                    }
                    handler?.postDelayed(runnable, 1000)
                    index+=5
                } else {
                    handler?.removeCallbacks(runnable)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Trip Ends", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        handler?.postDelayed(runnable, 2000)
        isRunning = true

    }
    ///Инициализируем карту
    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

}