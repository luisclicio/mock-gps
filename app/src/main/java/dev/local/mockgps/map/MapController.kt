package dev.local.mockgps.map

import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import dev.local.mockgps.geo.Coordinates
import dev.local.mockgps.geo.CoordinateValidator

class MapController(
    private val mapView: MapView,
    private val styleUrl: String,
    private var selectedCoordinates: Coordinates?,
    private val onCoordinatesSelected: (Coordinates) -> Unit,
    private val onMapLoaded: () -> Unit,
    private val onMapError: () -> Unit,
) {
    private var map: MapLibreMap? = null
    private var markerSource: GeoJsonSource? = null

    fun initialize() {
        mapView.addOnDidFailLoadingMapListener {
            onMapError()
        }

        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.setAttributionEnabled(true)
            readyMap.setStyle(styleUrl) { style ->
                val source = GeoJsonSource(SOURCE_ID, featureCollection(selectedCoordinates))
                style.addSource(source)
                style.addLayer(
                    CircleLayer(LAYER_ID, SOURCE_ID).withProperties(
                        circleColor(MARKER_COLOR),
                        circleRadius(9f),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(3f),
                    ),
                )
                markerSource = source

                readyMap.addOnMapClickListener { point ->
                    val coordinates = Coordinates(point.latitude, point.longitude)
                    if (CoordinateValidator.isValid(coordinates)) {
                        selectedCoordinates = coordinates
                        updateMarker()
                        onCoordinatesSelected(coordinates)
                    }
                    true
                }

                if (selectedCoordinates != null) {
                    moveCamera(selectedCoordinates!!, zoom = SELECTED_ZOOM)
                } else {
                    readyMap.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(BRAZIL_CENTER_LATITUDE, BRAZIL_CENTER_LONGITUDE))
                        .zoom(BRAZIL_ZOOM)
                        .build()
                }
                onMapLoaded()
            }
        }
    }

    fun setSelectedCoordinates(coordinates: Coordinates, moveCamera: Boolean = true) {
        if (!CoordinateValidator.isValid(coordinates)) return
        selectedCoordinates = coordinates
        updateMarker()
        if (moveCamera) moveCamera(coordinates, SELECTED_ZOOM)
    }

    private fun updateMarker() {
        markerSource?.setGeoJson(featureCollection(selectedCoordinates))
    }

    private fun moveCamera(coordinates: Coordinates, zoom: Double) {
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(coordinates.latitude, coordinates.longitude))
            .zoom(zoom)
            .build()
    }

    private fun featureCollection(coordinates: Coordinates?): FeatureCollection {
        if (coordinates == null) return FeatureCollection.fromFeatures(emptyArray<Feature>())
        return FeatureCollection.fromFeatures(
            arrayOf(
                Feature.fromGeometry(Point.fromLngLat(coordinates.longitude, coordinates.latitude)),
            ),
        )
    }

    private companion object {
        const val SOURCE_ID = "selected-location-source"
        const val LAYER_ID = "selected-location-marker"
        const val MARKER_COLOR = "#1769AA"
        const val BRAZIL_CENTER_LATITUDE = -15.7939
        const val BRAZIL_CENTER_LONGITUDE = -47.8828
        const val BRAZIL_ZOOM = 3.8
        const val SELECTED_ZOOM = 15.0
    }
}
