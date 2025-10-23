package com.example.smallbasket.repository

import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.models.*
import java.text.SimpleDateFormat
import java.util.*

class OrderRepository {
    private val api = RetrofitClient.apiService

    // ============================================
    // REQUEST OPERATIONS
    // ============================================

    suspend fun createOrder(request: CreateOrderRequest): Result<Order> {
        return try {
            val response = api.createOrder(request)
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

    suspend fun getAllOrders(status: String? = null, area: String? = null): Result<List<Order>> {
        return try {
            // Convert frontend status to backend status
            val backendStatus = when(status) {
                "pending" -> "open"
                "picked_up", "delivered" -> "completed"
                else -> status
            }

            val response = api.getAllOrders(backendStatus, area, null)
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

    suspend fun getUserOrders(): Result<List<Order>> {
        return try {
            val response = api.getUserOrders()
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

    suspend fun getAcceptedOrders(): Result<List<Order>> {
        return try {
            val response = api.getAcceptedOrders()
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

    suspend fun getOrder(orderId: String): Result<Order> {
        return try {
            val response = api.getOrder(orderId)
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

    suspend fun acceptOrder(orderId: String, delivererId: String = "", estimatedPrice: Double = 0.0): Result<Order> {
        return try {
            val request = AcceptOrderRequest(orderId)
            val response = api.acceptOrder(request)
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

    suspend fun updateOrderStatus(orderId: String, status: String): Result<Order> {
        return try {
            // Map frontend status to backend status
            val backendStatus = when(status) {
                "picked_up", "delivered" -> "completed"
                "pending" -> "open"
                else -> status
            }

            val request = UpdateOrderStatusRequest(orderId, backendStatus)
            val response = api.updateOrderStatus(request)
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

    // ============================================
    // USER OPERATIONS
    // ============================================

    suspend fun getUserStats(userId: String = ""): Result<UserStats> {
        return try {
            val response = api.getUserStats()
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

    suspend fun getUserProfile(): Result<UserProfileResponse> {
        return try {
            val response = api.getUserProfile()
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

    // ============================================
    // CONNECTIVITY OPERATIONS
    // ============================================

    suspend fun updateConnectivity(isConnected: Boolean, locationGranted: Boolean): Result<SuccessResponse> {
        return try {
            val request = ConnectivityUpdateRequest(isConnected, locationGranted)
            val response = api.updateConnectivity(request)
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

    // ============================================
    // AREA OPERATIONS
    // ============================================

    suspend fun getAvailableAreas(): Result<AreasListResponse> {
        return try {
            val response = api.getAvailableAreas()
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

    suspend fun setPreferredAreas(areas: List<String>): Result<SuccessResponse> {
        return try {
            val request = PreferredAreasRequest(areas)
            val response = api.setPreferredAreas(request)
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

    // ============================================
    // NOTIFICATION OPERATIONS
    // ============================================

    suspend fun registerFCMToken(token: String): Result<SuccessResponse> {
        return try {
            val request = FCMTokenRequest(token)
            val response = api.registerFCMToken(request)
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

    suspend fun unregisterFCMToken(): Result<SuccessResponse> {
        return try {
            val response = api.unregisterFCMToken()
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