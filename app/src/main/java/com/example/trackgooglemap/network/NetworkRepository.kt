package com.example.trackgooglemap.network

class NetworkRepository(private val api : ApiService) {

    suspend fun getCoordinates() = api.getCoordinates()

}