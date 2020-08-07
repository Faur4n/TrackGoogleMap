package com.example.trackgooglemap.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.trackgooglemap.MainViewModel
import com.example.trackgooglemap.network.ApiService
import com.example.trackgooglemap.network.NetworkRepository


class ViewModelFactory(private val apiService: ApiService) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                NetworkRepository(apiService)
            ) as T
        }
        throw IllegalArgumentException("Unknown class name")
    }

}