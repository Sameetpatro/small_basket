package com.example.smallbasket.api

import com.example.smallbasket.models.*
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
    suspend fun updateConnectivity(@Body request: ConnectivityUpdateRequest): Response<SuccessResponse>

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
}