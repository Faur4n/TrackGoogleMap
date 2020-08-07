package com.huawei.mapstrack.network

import com.example.trackgooglemap.network.dto.Coordinates


fun toCoordinates(data : List<List<String>>): List<Coordinates>
    {
        val result = emptyList<Coordinates>().toMutableList()

        data.forEach{
            result.add(Coordinates(
                time = it[0],
                latitude = it[2].toDouble(),
                longitude = it[1].toDouble()
                )
            )
        }

        return result
    }
