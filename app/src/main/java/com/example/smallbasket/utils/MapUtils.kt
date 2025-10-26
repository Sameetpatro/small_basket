// File: app/src/main/java/com/example/smallbasket/utils/MapUtils.kt
package com.example.smallbasket.utils

import android.content.Context
import android.graphics.Color
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.sources.GeoJsonSource
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.example.smallbasket.models.MapUserData
import android.util.Log
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.heatmapDensity
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.rgba
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom

object MapUtils {

    private const val TAG = "MapUtils"
    private const val HEATMAP_SOURCE_ID = "user-heatmap-source"
    private const val HEATMAP_LAYER_ID = "user-heatmap-layer"

    /**
     * Add heatmap layer directly on the map showing user density
     * Your red pointer stays on top
     */
    fun addHeatmapLayer(
        map: MapLibreMap,
        users: List<MapUserData>,
        currentUserLocation: LatLng?
    ) {
        val style = map.style
        if (style == null) {
            Log.w(TAG, "Map style not loaded yet")
            return
        }

        Log.d(TAG, "Adding heatmap for ${users.size} users")

        // Remove existing heatmap if present
        removeHeatmapLayer(map)

        // Filter out current user's location from heatmap
        val otherUsers = if (currentUserLocation != null) {
            users.filter { user ->
                val distance = calculateDistance(
                    user.latitude, user.longitude,
                    currentUserLocation.latitude, currentUserLocation.longitude
                )
                distance > 50.0 // Exclude if within 50 meters
            }
        } else {
            users
        }

        if (otherUsers.isEmpty()) {
            Log.d(TAG, "No other users to display in heatmap")
            return
        }

        Log.d(TAG, "Creating heatmap for ${otherUsers.size} other users")

        // Create GeoJSON for other users
        val geoJson = createGeoJsonFromUsers(otherUsers)

        // Add source
        val source = GeoJsonSource(HEATMAP_SOURCE_ID, geoJson)
        style.addSource(source)

        // Create beautiful heatmap layer with gradient
        val heatmapLayer = HeatmapLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID)
            .withProperties(
                // Heatmap weight (how much each point contributes)
                heatmapWeight(
                    interpolate(
                        linear(),
                        get("weight"),
                        stop(0, 0f),
                        stop(6, 1f)
                    )
                ),

                // Heatmap intensity based on zoom
                heatmapIntensity(
                    interpolate(
                        linear(),
                        zoom(),
                        stop(0, 0.5f),
                        stop(9, 2f),
                        stop(15, 3f)
                    )
                ),

                // Beautiful color gradient
                // Transparent -> Blue -> Cyan -> Green -> Yellow -> Orange -> Red
                heatmapColor(
                    interpolate(
                        linear(),
                        heatmapDensity(),
                        literal(0.0), rgba(0, 0, 255, 0),          // Transparent
                        literal(0.1), rgba(65, 105, 225, 0.4),     // Royal Blue
                        literal(0.3), rgba(0, 191, 255, 0.6),      // Deep Sky Blue
                        literal(0.5), rgba(0, 255, 127, 0.7),      // Spring Green
                        literal(0.7), rgba(255, 255, 0, 0.8),      // Yellow
                        literal(0.85), rgba(255, 165, 0, 0.9),     // Orange
                        literal(1.0), rgba(255, 0, 0, 1.0)         // Red
                    )
                ),

                // Heatmap radius - how wide each point spreads
                heatmapRadius(
                    interpolate(
                        linear(),
                        zoom(),
                        stop(0, 5f),
                        stop(5, 10f),
                        stop(10, 25f),
                        stop(15, 50f),
                        stop(20, 100f)
                    )
                ),

                // Heatmap opacity
                heatmapOpacity(
                    interpolate(
                        linear(),
                        zoom(),
                        stop(7, 0.8f),
                        stop(15, 0.6f),
                        stop(22, 0.4f)
                    )
                )
            )

        // Add layer - it will appear below markers automatically
        style.addLayer(heatmapLayer)

        Log.d(TAG, "✓ Heatmap layer added successfully with ${otherUsers.size} points")
    }

    /**
     * Remove heatmap layer from map
     */
    fun removeHeatmapLayer(map: MapLibreMap) {
        val style = map.style ?: return

        try {
            style.getLayer(HEATMAP_LAYER_ID)?.let {
                style.removeLayer(it)
                Log.d(TAG, "Removed heatmap layer")
            }

            style.getSource(HEATMAP_SOURCE_ID)?.let {
                style.removeSource(it)
                Log.d(TAG, "Removed heatmap source")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing heatmap", e)
        }
    }

    /**
     * Update heatmap with new user data
     */
    fun updateHeatmap(
        map: MapLibreMap,
        users: List<MapUserData>,
        currentUserLocation: LatLng?
    ) {
        val style = map.style ?: return

        val otherUsers = if (currentUserLocation != null) {
            users.filter { user ->
                val distance = calculateDistance(
                    user.latitude, user.longitude,
                    currentUserLocation.latitude, currentUserLocation.longitude
                )
                distance > 50.0
            }
        } else {
            users
        }

        Log.d(TAG, "Updating heatmap with ${otherUsers.size} users")

        val source = style.getSourceAs<GeoJsonSource>(HEATMAP_SOURCE_ID)
        if (source != null) {
            val geoJson = createGeoJsonFromUsers(otherUsers)
            source.setGeoJson(geoJson)
            Log.d(TAG, "✓ Heatmap updated")
        } else {
            addHeatmapLayer(map, users, currentUserLocation)
        }
    }

    /**
     * Create GeoJSON FeatureCollection from users
     */
    private fun createGeoJsonFromUsers(users: List<MapUserData>): String {
        val featureCollection = JsonObject()
        featureCollection.addProperty("type", "FeatureCollection")

        val features = JsonArray()

        users.forEach { user ->
            val feature = JsonObject()
            feature.addProperty("type", "Feature")

            val geometry = JsonObject()
            geometry.addProperty("type", "Point")

            val coordinates = JsonArray()
            coordinates.add(user.longitude)
            coordinates.add(user.latitude)
            geometry.add("coordinates", coordinates)

            feature.add("geometry", geometry)

            // Add properties with weight for heatmap intensity
            val properties = JsonObject()
            properties.addProperty("name", user.name ?: user.email)
            properties.addProperty("area", user.currentArea)
            properties.addProperty("weight", 1.0) // Each user contributes equally
            feature.add("properties", properties)

            features.add(feature)
        }

        featureCollection.add("features", features)

        return featureCollection.toString()
    }

    /**
     * Calculate distance between two points in meters
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    /**
     * Get area statistics for logging/display
     */
    fun getHeatmapStats(
        users: List<MapUserData>,
        currentUserLocation: LatLng?
    ): HeatmapStats {
        val otherUsers = if (currentUserLocation != null) {
            users.filter { user ->
                val distance = calculateDistance(
                    user.latitude, user.longitude,
                    currentUserLocation.latitude, currentUserLocation.longitude
                )
                distance > 50.0
            }
        } else {
            users
        }

        val areaGroups = otherUsers.groupBy { it.currentArea ?: "Unknown" }
        val areaCounts = areaGroups.mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        return HeatmapStats(
            totalUsers = otherUsers.size,
            topAreas = areaCounts.take(5)
        )
    }
}

/**
 * Statistics about the heatmap
 */
data class HeatmapStats(
    val totalUsers: Int,
    val topAreas: List<Pair<String, Int>>
)