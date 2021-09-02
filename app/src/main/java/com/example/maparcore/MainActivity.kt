package com.example.maparcore

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import com.example.maparcore.data.*
import com.example.maparcore.databinding.ActivityMainBinding
import com.example.maparcore.network.KakaoAPI
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.ar.sceneform.AnchorNode
import com.gun0912.tedpermission.TedPermission
import net.daum.mf.map.api.MapCircle
import net.daum.mf.map.api.MapPOIItem
import net.daum.mf.map.api.MapPoint
import net.daum.mf.map.api.MapView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.*
import kotlin.math.sign

class MainActivity : AppCompatActivity(), MapView.MapViewEventListener, SensorEventListener {

    private val binding : ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val mapView by lazy { MapView(this) }
    private val markers : MutableList<MapPOIItem> = emptyList<MapPOIItem>().toMutableList()
    private val marker = MapPOIItem()
    private var myLatitude : Double = 0.0
    private var myLongitude : Double = 0.0
    private var currentLocation: Location? = null

    private var places: MutableList<Place> = mutableListOf()

    private lateinit var arFragment: PlacesArFragment

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Sensor
    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var anchorNode: AnchorNode? = null

    private lateinit var lf : FusedLocationProviderClient

    private val Location.latLng : LatLng
        get() = LatLng(this.latitude, this.longitude)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initViews()

    }


    private fun initViews(){
        // 카카오 지도 생성
        initMap()

        // 내 위치 받아오기
        getMyLocation()

        // arFragment 설정
        initArFragment()

        sensorManager = getSystemService()!!
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setUpAr()

    }


    /**
     * 카카오 지도 생성
     * */
    private fun initMap(){
        binding.mapView.addView(mapView)
        mapView.setMapViewEventListener(this)
    }

    /**
     * 내 위치 받아오기
     * */
    private fun getMyLocation(){
        lf = LocationServices.getFusedLocationProviderClient(this)
        if(checkPermission()){
            lf.lastLocation.addOnCompleteListener {
                val location = it.result
                location?.let{
                    val geoCoder = Geocoder(this, Locale.KOREA)
                    try {
                        val address = geoCoder.getFromLocation(location.latitude, location.longitude, 1)
                        myLatitude = address[0].latitude
                        myLongitude = address[0].longitude
                        currentLocation = location
                        mapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(myLatitude, myLongitude), 1, true)
                        setMarker(myLatitude, myLongitude, "내 위치", 1)
                    }catch (e : IOException){
                        e.printStackTrace()
                    }
                }
            }
        }else{
        }
    }

    // 퍼미션 체크
    private fun checkPermission() : Boolean{
        return (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    // 마커 표시
    private fun setMarker(latitude : Double, longitude : Double, itemName : String, tag : Int){
        mapView.poiItems.forEach {
            if(it.mapPoint.mapPointGeoCoord.latitude == latitude && it.mapPoint.mapPointGeoCoord.longitude == longitude)
                return
            else{
                Log.e("+++++", "${it.mapPoint.mapPointGeoCoord.latitude} - $latitude / ${it.mapPoint.mapPointGeoCoord.longitude} - $longitude")
            }
        }
        val marker = MapPOIItem()
        marker.itemName = itemName
        marker.tag = tag
        markers.add(marker)
        if(itemName == "내 위치"){
//            marker.markerType = MapPOIItem.MarkerType.YellowPin // 내 위치는 YellowPin 마커 모양.
            marker.markerType = MapPOIItem.MarkerType.CustomImage   // 마커 타입을 커스텀으로 설정
            marker.customImageResourceId = R.drawable.my_location   // 커스텀 이미지 등록
            marker.isCustomImageAutoscale = false                   // hdpi, xhdpi 등 안드로이드 플랫폼의 스케일을 사용할 경우 지도 라이브러리의 스케일 기능 꺼줌
            marker.setCustomImageAnchor(0.5f, 1.0f)     // 마커 이미지 중 기준이 되는 위치(앵커포인트) 지정 - 마커 이미지 좌측 상단 기준 x(0.0f~1.0f), y(0.0f ~ 1.0f)
            searchKeyword("서른커피")
        }else{
            marker.markerType = MapPOIItem.MarkerType.BluePin // 기본으로 제공하는 BluePin 마커 모양.
        }

        marker.mapPoint = MapPoint.mapPointWithGeoCoord(latitude, longitude)

        mapView.addPOIItem(marker)
    }

    // 원 표시
    private fun setCircle(latitude : Double, longitude : Double, radius : Int, strokeColor : Int, fillColor : Int){
        var circle1 = MapCircle(MapPoint.mapPointWithGeoCoord(latitude, longitude), radius, strokeColor, fillColor)
        circle1.tag = 1234

        mapView.addCircle(circle1)
    }


    // 키워드 검색 함수
    private fun searchKeyword(keyword: String) {
        val retrofit = Retrofit.Builder()   // Retrofit 구성
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(KakaoAPI::class.java)   // 통신 인터페이스를 객체로 생성
        val call = api.getSearchKeyword(API_KEY, keyword, myLongitude.toString(), myLatitude.toString())   // 검색 조건 입력

        // API 서버에 요청
        call.enqueue(object: Callback<ResultSearchKeyword> {
            override fun onResponse(
                call: Call<ResultSearchKeyword>,
                response: Response<ResultSearchKeyword>
            ) {
                // 통신 성공 (검색 결과는 response.body()에 담겨있음)
                Log.d("Test", "Raw: ${response.raw()}")
                Log.d("Test", "Body: ${response.body()}")
                val result = response.body()?.documents
                result?.forEach {
                    places.add(
                        Place(
                            id = "",
                            icon = "",
                            name = it.place_name,
                            geometry = Geometry(location = GeometryLocation(it.y.toDouble(), it.x.toDouble()))
                        )
                    )
                }
            }

            override fun onFailure(call: Call<ResultSearchKeyword>, t: Throwable) {
                // 통신 실패
                Log.w("MainActivity", "통신 실패: ${t.message}")
            }
        })
    }


    /**
     * arFragment 설정
     * */
    private fun initArFragment(){
        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as PlacesArFragment
    }

    private fun setUpAr(){
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            val anchor = hitResult.createAnchor()
            anchorNode = AnchorNode(anchor)
            anchorNode?.setParent(arFragment.arSceneView.scene)
            addPlaces(anchorNode!!)
        }
    }

    private fun addPlaces(anchorNode: AnchorNode){
        val currentLocation = currentLocation
        if(currentLocation == null){
            Log.w("addPlaces", "Location has not been determined yet")
            return
        }

        val places = places
        if(places.isEmpty()){
            Log.w("addPlaces", "No Places to put")
            return
        }

        places.forEach { place ->
            // Add the place in AR
            val placeNode = PlaceNode(this, place)
            placeNode.setParent(anchorNode)
            placeNode.localPosition = place.getPositionVector(orientationAngles[0], currentLocation.latLng)
            placeNode.setOnTapListener { _, _ ->
                showInfoWindow(place)
            }

            // Add the place in mpas
            setMarker(place.geometry.location.lat, place.geometry.location.lng, place.name, 0)
        }
    }

    private fun showInfoWindow(place : Place){
        // Show in AR
        val matchingPlaceNode =
            anchorNode?.children
                ?.filterIsInstance<PlaceNode>()
                ?.first{
                    val otherPlace = it.place ?: return@first false
                    return@first otherPlace == place
                }
        matchingPlaceNode?.showInfoWindow()

        // Show as marker
        val matchingMarker = markers.firstOrNull{
            // todo kakao map tag 는 int 만 가능하고 구글의 경우 object 타입임
            val placeTag = (it.tag as? Place) ?: return@firstOrNull false
            return@firstOrNull placeTag == place
        }
//        matchingMarker?.showInfoWindow()
    }







    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onMapViewInitialized(p0: MapView?) {}

    override fun onMapViewCenterPointMoved(p0: MapView?, p1: MapPoint?) {}

    override fun onMapViewZoomLevelChanged(p0: MapView?, p1: Int) {}

    override fun onMapViewSingleTapped(p0: MapView?, p1: MapPoint?) {}

    override fun onMapViewDoubleTapped(p0: MapView?, p1: MapPoint?) {}

    override fun onMapViewLongPressed(p0: MapView?, p1: MapPoint?) {}

    override fun onMapViewDragStarted(p0: MapView?, p1: MapPoint?) {}

    override fun onMapViewDragEnded(p0: MapView?, p1: MapPoint?) {}

    override fun onMapViewMoveFinished(p0: MapView?, p1: MapPoint?) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event == null) return

        if(event.sensor.type == Sensor.TYPE_ACCELEROMETER){
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        }else if(event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD){
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
    }

    companion object {
        const val BASE_URL = "https://dapi.kakao.com/"
        const val API_KEY = "KakaoAK dd375dde1d04021e99fa6f9b56d69b56"  // REST API 키
    }
}