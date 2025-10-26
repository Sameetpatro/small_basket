// File: app/src/main/java/com/example/smallbasket/repository/MapRepository.kt
package com.example.smallbasket.repository

import android.util.Log
import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.api.NearbyUsersRequest
import com.example.smallbasket.models.NearbyUsersResponse
import com.example.smallbasket.api.MyGPSLocationResponse
import com.example.smallbasket.api.UsersInAreaResponse

class MapRepository {
    companion object {
        private const val TAG = "MapRepository"
    }

    private val api = RetrofitClient.apiService

    /**
     * FIXED: Get nearby users with detailed logging
     */
    suspend fun getNearbyUsers(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 5000.0
    ): Result<NearbyUsersResponse> {
        return try {
            Log.d(TAG, "=== Fetching nearby users ===")
            Log.d(TAG, "  Lat: $latitude")
            Log.d(TAG, "  Lng: $longitude")
            Log.d(TAG, "  Radius: ${radiusMeters}m")

            val request = NearbyUsersRequest(latitude, longitude, radiusMeters)
            val response = api.getNearbyUsers(request)

            Log.d(TAG, "Response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "✓ SUCCESS!")
                Log.d(TAG, "  Total users found: ${body.total}")
                Log.d(TAG, "  Users list: ${body.users.map { it.name ?: it.email }}")

                // Log each user's details
                body.users.forEachIndexed { index, user ->
                    Log.d(TAG, "  User $index:")
                    Log.d(TAG, "    - Name: ${user.name}")
                    Log.d(TAG, "    - Email: ${user.email}")
                    Log.d(TAG, "    - Location: (${user.latitude}, ${user.longitude})")
                    Log.d(TAG, "    - Area: ${user.currentArea}")
                    Log.d(TAG, "    - Reachable: ${user.isReachable}")
                }

                Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "✗ FAILED!")
                Log.e(TAG, "  Error code: ${response.code()}")
                Log.e(TAG, "  Error message: ${response.message()}")
                Log.e(TAG, "  Error body: $errorMsg")

                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ EXCEPTION while fetching nearby users", e)
            Log.e(TAG, "  Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Exception message: ${e.message}")
            e.printStackTrace()

            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Get user's current GPS location
     */
    suspend fun getMyGPSLocation(): Result<MyGPSLocationResponse> {
        return try {
            Log.d(TAG, "Fetching my GPS location from backend")
            val response = api.getMyGPSLocation()

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "✓ Got my GPS location")
                Log.d(TAG, "  Has location: ${body.hasLocation}")
                Log.d(TAG, "  Primary area: ${body.primaryArea}")

                Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "✗ Failed to get my GPS: $errorMsg")
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Exception getting my GPS location", e)
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
            Log.d(TAG, "Fetching users in area: $areaName")
            val response = api.getUsersInArea(areaName, includeEdgeUsers)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "✓ Found ${body.total} users in $areaName")
                Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "✗ Failed: $errorMsg")
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Exception", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}