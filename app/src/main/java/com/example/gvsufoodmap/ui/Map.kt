package com.example.gvsufoodmap.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gvsufoodmap.data.FakeRepo
import com.example.gvsufoodmap.model.Category
import com.example.gvsufoodmap.model.FoodLocation
import com.example.gvsufoodmap.state.AppState
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

@Composable
fun MapScreen() {
    val markerColorInt = AppState.markerColor.value
    val filters = AppState.filters.value
    val notes by AppState.notes   // user reviews / star ratings

    // Center camera roughly on GVSU Allendale campus
    val campusCenter = LatLng(42.963936, -85.888946)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(campusCenter, 16f)
    }

    var selectedLocationId by remember { mutableStateOf<String?>(null) }

    // Apply filter switches from Settings tab
    val filteredLocations = remember(filters) {
        FakeRepo.locations.filter { loc ->
            val categoryAllowed = when (loc.category) {
                Category.RESTAURANT -> filters.restaurant
                Category.STORE      -> filters.store
                Category.VENDING    -> filters.vending
                Category.CAFE       -> filters.cafe
                Category.OTHER      -> filters.other
            }
            categoryAllowed && (!filters.only24h || loc.open24h)
        }
    }

    val selectedLocation = filteredLocations.firstOrNull { it.id == selectedLocationId }
        ?: FakeRepo.locations.firstOrNull { it.id == selectedLocationId }

    val markerIcon = remember(markerColorInt) {
        markerIconFromArgb(markerColorInt)
    }

    Box(Modifier.fillMaxSize()) {
        // --- Actual Google Map ---
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true),
            properties = MapProperties(isMyLocationEnabled = false)
        ) {
            filteredLocations.forEach { loc ->
                val position = LatLng(loc.lat, loc.lng)
                val locReviews = notes.filter { it.locationId == loc.id }
                val avgRating = locReviews.map { it.rating }.average().takeIf { !it.isNaN() }

                Marker(
                    state = rememberMarkerState(position = position),
                    title = loc.name,
                    snippet = avgRating?.let {
                        "Rating: %.1f/5 (%d reviews)".format(it, locReviews.size)
                    } ?: "No reviews yet",
                    icon = markerIcon,
                    onClick = {
                        selectedLocationId = loc.id    // open bottom sheet
                        false                           // also show default info window
                    }
                )
            }
        }

        // --- Bottom card with “what they have” + reviews for the selected location ---
        if (selectedLocation != null) {
            val locationReviews = notes.filter { it.locationId == selectedLocation.id }
            LocationDetailsPanel(
                location = selectedLocation,
                reviews = locationReviews,
                onAddReview = { title, body, rating ->
                    AppState.addNote(selectedLocation.id, title, body, rating)
                },
                onDismiss = { selectedLocationId = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

// Use the RGB sliders from Settings to tint the marker on the real map
private fun markerIconFromArgb(argb: Int): BitmapDescriptor {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(r, g, b, hsv)
    return BitmapDescriptorFactory.defaultMarker(hsv[0])
}

@Composable
private fun LocationDetailsPanel(
    location: FoodLocation,
    reviews: List<AppState.Note>,
    onAddReview: (String, String, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(3f) }

    val avgRating = reviews.map { it.rating }.average().takeIf { !it.isNaN() }

    Surface(
        modifier = modifier,
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(location.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when (location.category) {
                            Category.RESTAURANT -> "Dining hall / restaurant"
                            Category.STORE      -> "Store / POD"
                            Category.VENDING    -> "Vending"
                            Category.CAFE       -> "Café"
                            Category.OTHER      -> "Other"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // “What they have”
            if (location.items.isNotEmpty()) {
                Text("What they have:", style = MaterialTheme.typography.labelMedium)
                location.items.forEach { item ->
                    Text("• $item", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Average rating + a few recent reviews
            if (avgRating != null) {
                Text(
                    "Average rating: %.1f/5 (%d reviews)".format(avgRating, reviews.size),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("No reviews yet – be the first!", style = MaterialTheme.typography.bodyMedium)
            }

            if (reviews.isNotEmpty()) {
                Divider()
                Text("Recent reviews:", style = MaterialTheme.typography.labelMedium)
                reviews.take(3).forEach { note ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                note.title.ifBlank { "(No title)" },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(note.body, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Rating: ${note.rating}/5",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Divider()

            // Add a new review for this location
            Text("Leave a review", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("What did you think?") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rating: ${rating.toInt()}/5")
                Slider(
                    value = rating,
                    onValueChange = { rating = it },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        val finalRating = rating.toInt().coerceIn(1, 5)
                        onAddReview(title, body, finalRating)
                        title = ""
                        body = ""
                        rating = 3f
                    }
                ) {
                    Text("Save review")
                }
            }
        }
    }
}
