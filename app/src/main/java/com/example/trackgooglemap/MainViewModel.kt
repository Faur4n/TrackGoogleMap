package com.example.trackgooglemap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.example.trackgooglemap.network.NetworkRepository
import com.example.offlinenews.utils.Resource
import com.huawei.mapstrack.network.toCoordinates
import kotlinx.coroutines.Dispatchers

class MainViewModel(private val networkRepository: NetworkRepository): ViewModel() {

    fun getCoordinates() = liveData(Dispatchers.IO){
        emit(Resource.loading(data = null))
        try{
            emit(
                Resource.success(
                data =  toCoordinates(networkRepository.getCoordinates())
                )
            )
        }catch (exception: Exception){
            emit(Resource.error(data = null,msg = exception.message ?: "Error Occurred!"))
        }
    }
}