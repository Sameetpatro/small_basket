package com.example.smallbasket.api

import com.example.smallbasket.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Order Endpoints
    @POST("orders/")
    suspend fun createOrder(@Body request: CreateOrderRequest): Response<ApiResponse<Order>>

    @GET("orders/")
    suspend fun getAllOrders(
        @Query("status") status: String? = null,
        @Query("area") area: String? = null
    ): Response<ApiResponse<List<Order>>>

    @GET("orders/{order_id}")
    suspend fun getOrder(@Path("order_id") orderId: String): Response<ApiResponse<Order>>

    @GET("orders/user/{user_id}")
    suspend fun getUserOrders(@Path("user_id") userId: String): Response<ApiResponse<List<Order>>>

    @POST("orders/{order_id}/accept")
    suspend fun acceptOrder(
        @Path("order_id") orderId: String,
        @Body request: AcceptOrderRequest
    ): Response<ApiResponse<Order>>

    @PATCH("orders/{order_id}/status")
    suspend fun updateOrderStatus(
        @Path("order_id") orderId: String,
        @Body request: UpdateOrderStatusRequest
    ): Response<ApiResponse<Order>>

    @DELETE("orders/{order_id}")
    suspend fun cancelOrder(@Path("order_id") orderId: String): Response<ApiResponse<String>>

    // User Stats
    @GET("users/{user_id}/stats")
    suspend fun getUserStats(@Path("user_id") userId: String): Response<ApiResponse<UserStats>>

    // Health Check
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, String>>
}