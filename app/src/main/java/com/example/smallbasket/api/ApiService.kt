package com.example.smallbasket.api

import com.example.smallbasket.models.*
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ============================================
    // REQUEST ENDPOINTS (Phase 3 Backend)
    // ============================================

    @POST("request/create")
    suspend fun createOrder(@Body request: CreateOrderRequest): Response<Order>

    @GET("request/all")
    suspend fun getAllOrders(
        @Query("status") status: String? = null,
        @Query("pickup_area") pickupArea: String? = null,
        @Query("drop_area") dropArea: String? = null
    ): Response<List<Order>>

    @GET("request/mine")
    suspend fun getUserOrders(): Response<List<Order>>

    @GET("request/accepted")
    suspend fun getAcceptedOrders(): Response<List<Order>>

    @GET("request/status/{request_id}")
    suspend fun getOrder(@Path("request_id") orderId: String): Response<Order>

    @POST("request/accept")
    suspend fun acceptOrder(@Body request: AcceptOrderRequest): Response<Order>

    @POST("request/update-status")
    suspend fun updateOrderStatus(@Body request: UpdateOrderStatusRequest): Response<Order>

    // ============================================
    // USER ENDPOINTS
    // ============================================

    @GET("user/stats")
    suspend fun getUserStats(): Response<RequestStatsResponse>

    @GET("user/profile")
    suspend fun getUserProfile(): Response<UserProfileResponse>

    // ============================================
    // CONNECTIVITY ENDPOINTS
    // ============================================

    @POST("user/connectivity/update")
    suspend fun updateConnectivity(
        @Body request: ConnectivityUpdateRequest
    ): Response<SuccessResponse>

    // ============================================
    // AREA ENDPOINTS
    // ============================================

    @GET("areas/list")
    suspend fun getAvailableAreas(): Response<AreasListResponse>

    @PUT("user/preferred-areas")
    suspend fun setPreferredAreas(@Body request: PreferredAreasRequest): Response<SuccessResponse>

    // ============================================
    // NOTIFICATION ENDPOINTS
    // ============================================

    @POST("notifications/register")
    suspend fun registerFCMToken(@Body request: FCMTokenRequest): Response<SuccessResponse>

    @DELETE("notifications/unregister")
    suspend fun unregisterFCMToken(): Response<SuccessResponse>

    // ============================================
    // HEALTH CHECK
    // ============================================

    @GET("/")
    suspend fun healthCheck(): Response<Map<String, Any>>

    // LOCATION/MAP ENDPOINTS
    // ============================================

    /**
     * Get nearby users within specified radius
     */
    @POST("location/nearby-users")
    suspend fun getNearbyUsers(
        @Body request: NearbyUsersRequest
    ): Response<NearbyUsersResponse>

    /**
     * Get user's current GPS location
     */
    @GET("location/my-gps")
    suspend fun getMyGPSLocation(): Response<MyGPSLocationResponse>

    /**
     * Get all users in a specific area
     */
    @GET("location/users-in-area/{area_name}")
    suspend fun getUsersInArea(
        @Path("area_name") areaName: String,
        @Query("include_edge_users") includeEdgeUsers: Boolean = true
    ): Response<UsersInAreaResponse>

    /**
     * Update user's GPS location
     */
    @POST("location/update-gps")
    suspend fun updateGPSLocation(
        @Body request: com.example.smallbasket.location.UpdateGPSLocationRequest
    ): Response<UpdateGPSLocationResponse>
}

data class UpdateGPSLocationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("fast_mode") val fastMode: Boolean,
    @SerializedName("data") val data: LocationUpdateData?
)

data class LocationUpdateData(
    @SerializedName("primary_area") val primaryArea: String?,
    @SerializedName("all_matching_areas") val allMatchingAreas: List<String>?,
    @SerializedName("is_on_edge") val isOnEdge: Boolean?,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

// Request model for nearby users
data class NearbyUsersRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("radius_meters") val radiusMeters: Double
)

// Response model for my GPS location
data class MyGPSLocationResponse(
    @SerializedName("has_location") val hasLocation: Boolean,
    @SerializedName("gps_location") val gpsLocation: GPSLocation?,
    @SerializedName("primary_area") val primaryArea: String?,
    @SerializedName("all_matching_areas") val allMatchingAreas: List<String>?,
    @SerializedName("is_on_edge") val isOnEdge: Boolean?,
    @SerializedName("nearby_areas") val nearbyAreas: List<String>?
)

data class GPSLocation(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float?,
    @SerializedName("last_updated") val lastUpdated: String?
)

// Response model for users in area
data class UsersInAreaResponse(
    @SerializedName("area") val area: String,
    @SerializedName("total") val total: Int,
    @SerializedName("include_edge_users") val includeEdgeUsers: Boolean,
    @SerializedName("users") val users: List<MapUserData>
)