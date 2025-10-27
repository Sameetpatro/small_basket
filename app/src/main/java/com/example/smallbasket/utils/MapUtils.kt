// File: app/src/main/java/com/example/smallbasket/utils/MapUtils.kt
package com.example.smallbasket.utils

import android.graphics.Color
import android.util.Log
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.sources.GeoJsonSource
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.example.smallbasket.models.MapUserData
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.*



object MapUtils {

    private const val TAG = "MapUtils"
    private const val HEATMAP_SOURCE_ID = "user-heatmap-source"
    private const val HEATMAP_LAYER_ID = "user-heatmap-layer"
    private const val AREA_CIRCLES_SOURCE_ID = "area-circles-source"
    private const val AREA_CIRCLES_LAYER_ID = "area-circles-layer"

    // Preferred areas with coordinates and radius
    data class PreferredArea(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double
    )

    private val preferredAreas = listOf(
        PreferredArea("SBIT", 28.9890834, 77.1506293 , 407.930),
        PreferredArea("Pallri", 28.9709633, 77.1531023, 1700.0),
        PreferredArea("Bahalgarh", 28.9470954 , 77.0835646 , 3200.0),
        PreferredArea("Sonepat", 28.9845887 , 77.0373188 , 3500.0),
        PreferredArea("TDI", 28.9098117 , 77.1307161,2300.0)
    )

    /**
     * MAIN METHOD: Add heatmap + gray circles for preferred areas
     */
    fun addHeatmapWithAreas(
        map: MapLibreMap,
        users: List<MapUserData>,
        currentUserLocation: LatLng?
    ) {
        val style = map.style
        if (style == null) {
            Log.e(TAG, "❌ Map style not loaded yet")
            return
        }

        Log.d(TAG, "=== STARTING HEATMAP WITH AREAS ===")
        Log.d(TAG, "Total users: ${users.size}")
        Log.d(TAG, "Current user location: $currentUserLocation")

        // Remove existing layers
        removeHeatmapLayer(map)
        removeAreaCircles(map)

        // Step 1: Draw gray circles for all preferred areas
        drawAreaCircles(map, users)

        // Step 2: Add heatmap for users (excluding current user)
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

        if (otherUsers.isNotEmpty()) {
            addHeatmapLayer(map, otherUsers)
            Log.d(TAG, "✓ Added heatmap for ${otherUsers.size} users")
        } else {
            Log.d(TAG, "⚠️ No users to display in heatmap")
        }

        Log.d(TAG, "=== HEATMAP WITH AREAS COMPLETE ===")
    }

    /**
     * Draw gray circles for preferred areas
     * If users exist in area, circle is slightly visible
     * If no users, gray circle is fully opaque
     */
    private fun drawAreaCircles(map: MapLibreMap, users: List<MapUserData>) {
        val style = map.style ?: return

        Log.d(TAG, "Drawing area circles for ${preferredAreas.size} areas")

        val featuresArray = JsonArray()

        preferredAreas.forEach { area ->
            // Count users in this area
            val usersInArea = users.count { user ->
                val distance = calculateDistance(
                    user.latitude, user.longitude,
                    area.latitude, area.longitude
                )
                distance <= area.radiusMeters
            }

            Log.d(TAG, "  ${area.name}: $usersInArea users (radius: ${area.radiusMeters}m)")

            // Create circle feature
            val feature = JsonObject()
            feature.addProperty("type", "Feature")

            val geometry = JsonObject()
            geometry.addProperty("type", "Point")

            val coordinates = JsonArray()
            coordinates.add(area.longitude)
            coordinates.add(area.latitude)
            geometry.add("coordinates", coordinates)

            feature.add("geometry", geometry)

            // Properties
            val properties = JsonObject()
            properties.addProperty("name", area.name)
            properties.addProperty("radius", area.radiusMeters)
            properties.addProperty("userCount", usersInArea)
            properties.addProperty("hasUsers", usersInArea > 0)
            feature.add("properties", properties)

            featuresArray.add(feature)
        }

        val featureCollection = JsonObject()
        featureCollection.addProperty("type", "FeatureCollection")
        featureCollection.add("features", featuresArray)

        // Add source
        val source = GeoJsonSource(AREA_CIRCLES_SOURCE_ID, featureCollection.toString())
        style.addSource(source)

        // Add circle layer with conditional styling
        val circleLayer = CircleLayer(AREA_CIRCLES_LAYER_ID, AREA_CIRCLES_SOURCE_ID)
            .withProperties(
                // Radius in pixels (convert meters to zoom-based pixels)
                circleRadius(
                    interpolate(
                        linear(),
                        zoom(),
                        stop(10, get("radius")),
                        stop(15, product(get("radius"), literal(2))),
                        stop(20, product(get("radius"), literal(4)))
                    )

            ),

                // Color: Gray for empty areas, slightly transparent for areas with users
                circleColor(
                    switchCase(
                        get("hasUsers"),
                        rgba(128, 128, 128, 0.3f), // Light gray if has users (heatmap will show)
                        rgba(128, 128, 128, 0.6f)  // Darker gray if no users
                    )
                ),

                // Stroke
                circleStrokeWidth(2f),
                circleStrokeColor(
                    switchCase(
                        get("hasUsers"),
                        rgba(100, 100, 100, 0.5f),
                        rgba(100, 100, 100, 0.8f)
                    )
                ),

                // Opacity
                circleOpacity(0.8f)
            )

        style.addLayerBelow(circleLayer, HEATMAP_LAYER_ID)
        Log.d(TAG, "✓ Area circles added")
    }

    /**
     * Add heatmap layer for users
     */
    private fun addHeatmapLayer(map: MapLibreMap, users: List<MapUserData>) {
        val style = map.style ?: return

        Log.d(TAG, "Creating heatmap for ${users.size} users")

        // Create GeoJSON for users
        val geoJson = createGeoJsonFromUsers(users)

        // Add source
        val source = GeoJsonSource(HEATMAP_SOURCE_ID, geoJson)
        style.addSource(source)

        // Create heatmap layer
        val heatmapLayer = HeatmapLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID)
            .withProperties(
                // Heatmap weight
                heatmapWeight(
                    interpolate(
                        linear(),
                        get("weight"),
                        stop(0, 0f),
                        stop(6, 2f) // Increased for stronger effect
                    )
                ),

                // Heatmap intensity
                heatmapIntensity(
                    interpolate(
                        linear(),
                        zoom(),
                        stop(0, 1f),
                        stop(9, 3f),
                        stop(15, 5f)
                    )
                ),

                // Color gradient - stronger colors
                heatmapColor(
                    interpolate(
                        linear(),
                        heatmapDensity(),
                        literal(0.0), rgba(0, 0, 255, 0),          // Transparent
                        literal(0.1), rgba(65, 105, 225, 0.6),     // Royal Blue
                        literal(0.3), rgba(0, 191, 255, 0.7),      // Deep Sky Blue
                        literal(0.5), rgba(0, 255, 127, 0.8),      // Spring Green
                        literal(0.7), rgba(255, 255, 0, 0.9),      // Yellow
                        literal(0.85), rgba(255, 165, 0, 0.95),    // Orange
                        literal(1.0), rgba(255, 0, 0, 1.0)         // Red
                    )
                ),

                // Heatmap radius - larger for better visibility
                heatmapRadius(
                    interpolate(
                        linear(),
                        zoom(),
                        stop(0, 10f),
                        stop(5, 20f),
                        stop(10, 40f),
                        stop(15, 80f),
                        stop(20, 150f)
                    )
                ),

                // Heatmap opacity
                heatmapOpacity(0.9f)
            )

        style.addLayer(heatmapLayer)
        Log.d(TAG, "✓ Heatmap layer added")
    }

    /**
     * Create GeoJSON from users with weight property
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

            val properties = JsonObject()
            properties.addProperty("name", user.name ?: user.email)
            properties.addProperty("weight", 1.5) // Increased weight for stronger heatmap
            feature.add("properties", properties)

            features.add(feature)
        }

        featureCollection.add("features", features)
        return featureCollection.toString()
    }

    /**
     * Remove heatmap layer
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
     * Remove area circles
     */
    private fun removeAreaCircles(map: MapLibreMap) {
        val style = map.style ?: return

        try {
            style.getLayer(AREA_CIRCLES_LAYER_ID)?.let {
                style.removeLayer(it)
                Log.d(TAG, "Removed area circles layer")
            }

            style.getSource(AREA_CIRCLES_SOURCE_ID)?.let {
                style.removeSource(it)
                Log.d(TAG, "Removed area circles source")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing area circles", e)
        }
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
     * Get demo users for testing (place users in each preferred area)
     */
    fun getDemoUsers(): List<MapUserData> {
        val demoUsers = mutableListOf<MapUserData>()

        preferredAreas.forEach { area ->
            // Add 3-5 demo users per area
            val userCount = (3..5).random()

            repeat(userCount) { i ->
                // Generate random offset within area radius
                val angle = Math.random() * 2 * Math.PI
                val distance = Math.random() * area.radiusMeters * 0.8 // 80% of radius

                val latOffset = distance * Math.cos(angle) / 111000.0 // ~111km per degree
                val lonOffset = distance * Math.sin(angle) / (111000.0 * Math.cos(Math.toRadians(area.latitude)))

                demoUsers.add(
                    MapUserData(
                        uid = "demo_${area.name}_$i",
                        name = "User ${area.name} $i",
                        email = "user${i}@${area.name.lowercase()}.com",
                        latitude = area.latitude + latOffset,
                        longitude = area.longitude + lonOffset,
                        accuracy = 10f,
                        lastUpdated = "2024-10-27T10:00:00Z",
                        currentArea = area.name,
                        isReachable = true
                    )
                )
            }
        }

        Log.d(TAG, "✓ Generated ${demoUsers.size} demo users")
        return demoUsers
    }

    /**
     * Get heatmap statistics
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

data class HeatmapStats(
    val totalUsers: Int,
    val topAreas: List<Pair<String, Int>>
)