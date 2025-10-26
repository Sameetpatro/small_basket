// File: app/src/main/java/com/example/smallbasket/repository/MapRepository.kt
package com.example.smallbasket.repository

import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.api.NearbyUsersRequest
import com.example.smallbasket.models.MapUserData
import com.example.smallbasket.models.NearbyUsersResponse
import com.example.smallbasket.api.MyGPSLocationResponse
import com.example.smallbasket.api.UsersInAreaResponse

class MapRepository {
    private val api = RetrofitClient.apiService

    /**
     * Get nearby users within specified radius
     */
    suspend fun getNearbyUsers(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 5000.0
    ): Result<NearbyUsersResponse> {
        return try {
            val request = NearbyUsersRequest(latitude, longitude, radiusMeters)
            val response = api.getNearbyUsers(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Get user's current GPS location
     */
    suspend fun getMyGPSLocation(): Result<MyGPSLocationResponse> {
        return try {
            val response = api.getMyGPSLocation()

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Get all users in a specific area
     */
    suspend fun getUsersInArea(
        areaName: String,
        includeEdgeUsers: Boolean = true
    ): Result<UsersInAreaResponse> {
        return try {
            val response = api.getUsersInArea(areaName, includeEdgeUsers)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}