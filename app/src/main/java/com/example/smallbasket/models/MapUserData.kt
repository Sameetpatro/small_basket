// File: app/src/main/java/com/example/smallbasket/models/MapUserData.kt
package com.example.smallbasket.models

import com.google.gson.annotations.SerializedName

/**
 * Model for user location data on map
 */
data class MapUserData(
    @SerializedName("uid") val uid: String,
    @SerializedName("name") val name: String?,
    @SerializedName("email") val email: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float?,
    @SerializedName("last_updated") val lastUpdated: String?,
    @SerializedName("current_area") val currentArea: String?,
    @SerializedName("is_reachable") val isReachable: Boolean = false
)

/**
 * Response model for nearby users
 */
data class NearbyUsersResponse(
    @SerializedName("total") val total: Int,
    @SerializedName("radius_meters") val radiusMeters: Double,
    @SerializedName("radius_km") val radiusKm: Double,
    @SerializedName("users") val users: List<MapUserData>
)