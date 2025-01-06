package com.shahin.humidtemp


import com.google.gson.annotations.SerializedName

data class DataModel(
    @SerializedName("humidity")
    val humidity: Double? = null,
    @SerializedName("temperature")
    val temperature: Double? = null,
    @SerializedName("timestamp")
    val timestamp: Int? = null
)