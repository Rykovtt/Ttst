package com.kartoteka.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kartoteka.app.data.Person
import com.kartoteka.app.ui.components.accentFor
import com.kartoteka.app.ui.components.initials
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

data class MapMarker(val key: String, val lat: Double, val lng: Double, val person: Person?, val title: String)

val DefaultCenter = GeoPoint(50.4501, 30.5234) // Киев

/**
 * Карта OpenStreetMap (osmdroid) — без ключей и аккаунтов.
 * @param fitKey при изменении камера заново охватывает все маркеры.
 */
@Composable
fun OsmMap(
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    fitKey: Any? = Unit,
    interactive: Boolean = true,
    onMarkerClick: (MapMarker) -> Unit = {},
    onTap: ((GeoPoint) -> Unit)? = null,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val markerClick = rememberUpdatedState(onMarkerClick)
    val tap = rememberUpdatedState(onTap)
    val state = remember { MapState() }

    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(5.0)
            controller.setCenter(DefaultCenter)
            if (dark) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            if (!interactive) setOnTouchListener { _, _ -> true }
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { tap.value?.invoke(p); return tap.value != null }
                override fun longPressHelper(p: GeoPoint): Boolean { tap.value?.invoke(p); return tap.value != null }
            }))
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }

    AndroidView(factory = { map }, modifier = modifier) { view ->
        if (state.markers != markers) {
            state.markers = markers
            view.overlays.removeAll { it is Marker }
            markers.forEach { m ->
                view.overlays.add(Marker(view).apply {
                    position = GeoPoint(m.lat, m.lng)
                    title = m.title
                    icon = BitmapDrawable(context.resources, pinBitmap(context, m.person))
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ -> markerClick.value(m); true }
                })
            }
            view.invalidate()
        }
        if (state.fitKey != fitKey) {
            state.fitKey = fitKey
            fit(view, markers)
        }
    }
}

private class MapState {
    var markers: List<MapMarker>? = null
    var fitKey: Any? = Any()
}

private fun fit(view: MapView, markers: List<MapMarker>) {
    val points = markers.map { GeoPoint(it.lat, it.lng) }
    val action = {
        when {
            points.isEmpty() -> Unit
            points.size == 1 -> { view.controller.setZoom(15.0); view.controller.setCenter(points[0]) }
            else -> {
                val box = BoundingBox.fromGeoPointsSafe(points)
                val padded = box.increaseByScale(1.3f)
                view.zoomToBoundingBox(padded, false, 64)
                if (view.zoomLevelDouble > 16) view.controller.setZoom(16.0)
            }
        }
    }
    if (view.width > 0) action() else view.addOnFirstLayoutListener { _, _, _, _, _ -> action() }
}

private val pinCache = HashMap<String, Bitmap>()

/** Круглая булавка с фото человека или его инициалами. */
fun pinBitmap(context: Context, person: Person?): Bitmap {
    val key = "${person?.id}_${person?.avatarPath}_${person?.displayName}"
    pinCache[key]?.let { return it }
    val d = context.resources.displayMetrics.density
    val size = (46 * d).toInt()
    val tail = (9 * d).toInt()
    val border = 3 * d
    val bmp = Bitmap.createBitmap(size, size + tail, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; setShadowLayer(3 * d, 0f, d, 0x55000000) }
    val r = size / 2f
    c.drawCircle(r, r, r - d, white)
    c.drawPath(Path().apply {
        moveTo(r - 7 * d, size - 6 * d); lineTo(r, (size + tail).toFloat() - d); lineTo(r + 7 * d, size - 6 * d); close()
    }, white)
    val inner = r - border - d
    val photo = person?.avatarPath?.let { path ->
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    if (photo != null) {
        val scale = (inner * 2) / minOf(photo.width, photo.height)
        val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate(r - photo.width * scale / 2, r - photo.height * scale / 2)
            })
        }
        c.drawCircle(r, r, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    } else {
        val color = if (person != null) accentFor(person.displayName).toArgb() else 0xFF4F3FD1.toInt()
        c.drawCircle(r, r, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val text = person?.let { initials(it) } ?: "•"
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE; textSize = 16 * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        c.drawText(text, r, r - (tp.descent() + tp.ascent()) / 2, tp)
    }
    pinCache[key] = bmp
    return bmp
}
