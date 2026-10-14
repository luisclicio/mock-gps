package dev.local.mockgps

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import dev.local.mockgps.geo.Coordinates
import dev.local.mockgps.geo.CoordinateError
import dev.local.mockgps.geo.CoordinateField
import dev.local.mockgps.geo.CoordinateValidation
import dev.local.mockgps.geo.CoordinateValidator
import dev.local.mockgps.location.MockLocationController
import dev.local.mockgps.location.MockLocationService
import dev.local.mockgps.map.MapController
import dev.local.mockgps.settings.RecentLocation
import dev.local.mockgps.settings.RecentLocationHistory
import dev.local.mockgps.settings.SettingsRepository
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var settings: SettingsRepository
    private lateinit var latitudeInput: EditText
    private lateinit var longitudeInput: EditText
    private lateinit var latitudeError: TextView
    private lateinit var longitudeError: TextView
    private lateinit var jitterSwitch: Switch
    private lateinit var applyButton: Button
    private lateinit var stopButton: Button
    private lateinit var recentLocationSpinner: Spinner
    private lateinit var recentLocationLabelButton: Button
    private lateinit var statusText: TextView
    private lateinit var mapView: MapView
    private lateinit var mapMessage: TextView

    private var mapController: MapController? = null
    private var suppressTextChanges = false
    private var receiverRegistered = false
    private var pendingCoordinates: Coordinates? = null
    private var pendingJitter = false
    private var currentStatus = MockLocationService.STATUS_INACTIVE
    private var statusMessage: String? = null
    private var activeCoordinates: Coordinates? = null
    private var activeJitter = false
    private var recentLocations: List<RecentLocation> = emptyList()
    private var selectedRecentLocation: RecentLocation? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            currentStatus = intent.getStringExtra(MockLocationService.EXTRA_STATUS)
                ?: MockLocationService.STATUS_INACTIVE
            statusMessage = intent.getStringExtra(MockLocationService.EXTRA_MESSAGE)

            val latitude = intent.getDoubleExtra(MockLocationService.EXTRA_LATITUDE, Double.NaN)
            val longitude = intent.getDoubleExtra(MockLocationService.EXTRA_LONGITUDE, Double.NaN)
            activeCoordinates = Coordinates(latitude, longitude).takeIf(CoordinateValidator::isValid)
            activeJitter = intent.getBooleanExtra(MockLocationService.EXTRA_JITTER_ENABLED, false)
            if (currentStatus == MockLocationService.STATUS_ACTIVE) {
                activeCoordinates?.let { coordinates ->
                    settings.recordRecentLocation(coordinates)
                    renderRecentLocations(selectedCoordinates = coordinates)
                }
            }
            renderStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)
        applySystemBarInsets()

        settings = SettingsRepository(this)
        latitudeInput = findViewById(R.id.latitudeInput)
        longitudeInput = findViewById(R.id.longitudeInput)
        latitudeError = findViewById(R.id.latitudeError)
        longitudeError = findViewById(R.id.longitudeError)
        jitterSwitch = findViewById(R.id.jitterSwitch)
        applyButton = findViewById(R.id.applyButton)
        stopButton = findViewById(R.id.stopButton)
        recentLocationSpinner = findViewById(R.id.recentLocationSpinner)
        recentLocationLabelButton = findViewById(R.id.recentLocationLabelButton)
        statusText = findViewById(R.id.statusText)
        mapView = findViewById(R.id.mapView)
        mapMessage = findViewById(R.id.mapMessage)

        val savedCoordinates = settings.loadCoordinates()
        if (savedCoordinates != null) setInputCoordinates(savedCoordinates)
        jitterSwitch.isChecked = settings.isJitterEnabled()

        setupActions()
        setupCoordinateInputs()
        mapView.onCreate(savedInstanceState)
        mapController = MapController(
            mapView = mapView,
            styleUrl = BuildConfig.MAP_STYLE_URL,
            selectedCoordinates = savedCoordinates,
            onCoordinatesSelected = ::onMapCoordinateSelected,
            onMapLoaded = { mapMessage.visibility = View.GONE },
            onMapError = {
                mapMessage.text = getString(R.string.map_error)
                mapMessage.visibility = View.VISIBLE
            },
        ).also(MapController::initialize)

        refreshPersistedStatus()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(MockLocationService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        refreshPersistedStatus()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver)
            receiverRegistered = false
        }
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    @Deprecated("Used by Android's runtime permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return

        val allGranted = runtimePermissions().all { permission ->
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            val coordinates = pendingCoordinates
            if (coordinates != null) startSimulation(coordinates, pendingJitter)
        } else {
            setStatus(MockLocationService.STATUS_ERROR, getString(R.string.permission_denied_body))
            showPermissionSettingsDialog()
        }
    }

    private fun setupActions() {
        applyButton.setOnClickListener { applySelectedCoordinates() }
        stopButton.setOnClickListener { stopSimulation() }
        setupRecentLocations()
        jitterSwitch.setOnCheckedChangeListener { _, enabled ->
            settings.saveJitterEnabled(enabled)
            if (currentStatus == MockLocationService.STATUS_ACTIVE) {
                val coordinates = activeCoordinates ?: settings.loadActiveCoordinates()
                if (coordinates != null) requestStart(coordinates, enabled)
            }
        }
        renderButtons()
    }

    private fun setupRecentLocations() {
        recentLocationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                val location = recentLocations.getOrNull(position - 1)
                selectedRecentLocation = location
                renderRecentLocationLabelButton()
                if (location != null) {
                    setInputCoordinates(location.coordinates)
                    clearValidationErrors()
                    settings.saveCoordinates(location.coordinates)
                    mapController?.setSelectedCoordinates(location.coordinates)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedRecentLocation = null
                renderRecentLocationLabelButton()
            }
        }
        recentLocationLabelButton.setOnClickListener { showRecentLocationLabelDialog() }
        renderRecentLocations()
    }

    private fun renderRecentLocations(selectedCoordinates: Coordinates? = null) {
        recentLocations = settings.loadRecentLocations()
        val prompt = getString(
            if (recentLocations.isEmpty()) {
                R.string.recent_locations_empty
            } else {
                R.string.recent_locations_prompt
            },
        )
        val options = buildList {
            add(prompt)
            recentLocations.forEach { location ->
                val label = location.label.ifBlank { getString(R.string.recent_location_unnamed) }
                add(
                    getString(
                        R.string.recent_location_option,
                        label,
                        formatCoordinate(location.coordinates.latitude),
                        formatCoordinate(location.coordinates.longitude),
                    ),
                )
            }
        }
        recentLocationSpinner.adapter = ArrayAdapter(
            this,
            R.layout.recent_location_spinner_item,
            options,
        ).apply {
            setDropDownViewResource(R.layout.recent_location_spinner_item)
        }

        val selectedIndex = recentLocations.indexOfFirst { it.coordinates == selectedCoordinates }
        recentLocationSpinner.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0)
        selectedRecentLocation = recentLocations.getOrNull(selectedIndex)
        renderRecentLocationLabelButton()
    }

    private fun renderRecentLocationLabelButton() {
        val location = selectedRecentLocation
        recentLocationLabelButton.isEnabled = location != null
        recentLocationLabelButton.setText(
            if (location?.label.isNullOrBlank()) {
                R.string.recent_location_name
            } else {
                R.string.recent_location_edit_name
            },
        )
    }

    private fun showRecentLocationLabelDialog() {
        val location = selectedRecentLocation ?: return
        val input = EditText(this).apply {
            hint = getString(R.string.recent_location_label_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(RecentLocationHistory.MAX_LABEL_LENGTH))
            setSingleLine(true)
            setText(location.label)
            setSelection(text.length)
            val horizontalPadding = (12 * resources.displayMetrics.density).toInt()
            val verticalPadding = (8 * resources.displayMetrics.density).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }
        AlertDialog.Builder(this)
            .setTitle(
                if (location.label.isBlank()) {
                    R.string.recent_location_dialog_add
                } else {
                    R.string.recent_location_dialog_edit
                },
            )
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                settings.saveRecentLocationLabel(location.coordinates, input.text.toString())
                renderRecentLocations(selectedCoordinates = location.coordinates)
                Toast.makeText(this, R.string.recent_location_label_saved, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setupCoordinateInputs() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressTextChanges) onCoordinateTextChanged()
            }
            override fun afterTextChanged(editable: Editable?) = Unit
        }
        latitudeInput.addTextChangedListener(watcher)
        longitudeInput.addTextChangedListener(watcher)
    }

    private fun onCoordinateTextChanged() {
        val validation = CoordinateValidator.validate(
            latitudeInput.text.toString(),
            longitudeInput.text.toString(),
        )
        if (validation is CoordinateValidation.Valid) {
            clearValidationErrors()
            settings.saveCoordinates(validation.coordinates)
            mapController?.setSelectedCoordinates(validation.coordinates)
            return
        }

        if (validation is CoordinateValidation.Invalid) {
            val currentValue = when (validation.field) {
                CoordinateField.LATITUDE -> latitudeInput.text.toString()
                CoordinateField.LONGITUDE -> longitudeInput.text.toString()
            }
            if (currentValue.isNotBlank()) showValidationError(validation)
            else clearValidationErrors()
        }
    }

    private fun applySelectedCoordinates() {
        when (val result = CoordinateValidator.validate(
            latitudeInput.text.toString(),
            longitudeInput.text.toString(),
        )) {
            is CoordinateValidation.Invalid -> {
                showValidationError(result)
                if (currentStatus != MockLocationService.STATUS_ACTIVE) {
                    setStatus(MockLocationService.STATUS_ERROR, validationMessage(result))
                }
            }
            is CoordinateValidation.Valid -> {
                clearValidationErrors()
                settings.saveCoordinates(result.coordinates)
                mapController?.setSelectedCoordinates(result.coordinates)
                requestStart(result.coordinates, jitterSwitch.isChecked)
            }
        }
    }

    private fun onMapCoordinateSelected(coordinates: Coordinates) {
        val latitude = formatCoordinate(coordinates.latitude)
        val longitude = formatCoordinate(coordinates.longitude)
        setInputCoordinates(Coordinates(latitude.toDouble(), longitude.toDouble()))
        clearValidationErrors()
        settings.saveCoordinates(Coordinates(latitude.toDouble(), longitude.toDouble()))
        mapController?.setSelectedCoordinates(
            Coordinates(latitude.toDouble(), longitude.toDouble()),
            moveCamera = false,
        )
        Toast.makeText(this, R.string.map_selected, Toast.LENGTH_SHORT).show()
    }

    private fun setInputCoordinates(coordinates: Coordinates) {
        suppressTextChanges = true
        latitudeInput.setText(coordinates.latitude.toString())
        longitudeInput.setText(coordinates.longitude.toString())
        suppressTextChanges = false
    }

    private fun requestStart(coordinates: Coordinates, useJitter: Boolean) {
        pendingCoordinates = coordinates
        pendingJitter = useJitter
        val missingPermissions = runtimePermissions().filter { permission ->
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            setStatus(MockLocationService.STATUS_ERROR, getString(R.string.permission_body))
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_body)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        startSimulation(coordinates, useJitter)
    }

    private fun startSimulation(coordinates: Coordinates, useJitter: Boolean) {
        if (!locationServicesEnabled()) {
            setStatus(MockLocationService.STATUS_ERROR, getString(R.string.location_services_body))
            AlertDialog.Builder(this)
                .setTitle(R.string.location_services_title)
                .setMessage(R.string.location_services_body)
                .setPositiveButton(R.string.open_location_settings) { _, _ ->
                    openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        if (!MockLocationController.isMockLocationAuthorized(this)) {
            setStatus(MockLocationService.STATUS_PERMISSION_REQUIRED, getString(R.string.mock_location_body))
            AlertDialog.Builder(this)
                .setTitle(R.string.mock_location_title)
                .setMessage(R.string.mock_location_body)
                .setPositiveButton(R.string.open_developer_options) { _, _ ->
                    openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        setStatus(MockLocationService.STATUS_STARTING)
        val intent = Intent(this, MockLocationService::class.java)
            .setAction(MockLocationService.ACTION_START)
            .putExtra(MockLocationService.EXTRA_LATITUDE, coordinates.latitude)
            .putExtra(MockLocationService.EXTRA_LONGITUDE, coordinates.longitude)
            .putExtra(MockLocationService.EXTRA_JITTER_ENABLED, useJitter)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: SecurityException) {
            setStatus(MockLocationService.STATUS_ERROR, getString(R.string.error_start_location))
        } catch (_: RuntimeException) {
            setStatus(MockLocationService.STATUS_ERROR, getString(R.string.error_start_location))
        }
    }

    private fun stopSimulation() {
        setStatus(MockLocationService.STATUS_STOPPING)
        try {
            startService(
                Intent(this, MockLocationService::class.java)
                    .setAction(MockLocationService.ACTION_STOP),
            )
        } catch (_: RuntimeException) {
            setStatus(MockLocationService.STATUS_ERROR, getString(R.string.error_provider_cleanup))
        }
    }

    private fun runtimePermissions(): List<String> = buildList {
        add(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Manifest.permission.ACCESS_FINE_LOCATION
            } else {
                Manifest.permission.ACCESS_COARSE_LOCATION
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun locationServicesEnabled(): Boolean {
        val locationManager = getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF,
            ) != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private fun refreshPersistedStatus() {
        if (settings.isSimulationActive() && isServiceRunning()) {
            currentStatus = MockLocationService.STATUS_ACTIVE
            activeCoordinates = settings.loadActiveCoordinates()
            activeJitter = settings.isActiveJitterEnabled()
            statusMessage = null
        } else if (settings.isSimulationActive()) {
            settings.setSimulationActive(false)
            currentStatus = MockLocationService.STATUS_INACTIVE
            activeCoordinates = null
            statusMessage = null
        } else {
            currentStatus = MockLocationService.STATUS_INACTIVE
            activeCoordinates = null
            statusMessage = null
            val error = settings.loadError()
            if (error != null) {
                currentStatus = MockLocationService.STATUS_ERROR
                statusMessage = error
            }
        }
        renderStatus()
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return manager.getRunningServices(100).any {
            it.service.className == MockLocationService::class.java.name
        }
    }

    private fun showValidationError(error: CoordinateValidation.Invalid) {
        clearValidationErrors()
        val view = if (error.field == CoordinateField.LATITUDE) latitudeError else longitudeError
        view.text = validationMessage(error)
        view.visibility = View.VISIBLE
    }

    private fun clearValidationErrors() {
        latitudeError.text = ""
        longitudeError.text = ""
        latitudeError.visibility = View.GONE
        longitudeError.visibility = View.GONE
    }

    private fun validationMessage(error: CoordinateValidation.Invalid): String {
        val resource = when (error.field) {
            CoordinateField.LATITUDE -> when (error.error) {
                CoordinateError.REQUIRED -> R.string.error_latitude_required
                CoordinateError.NOT_A_NUMBER -> R.string.error_latitude_number
                CoordinateError.OUT_OF_RANGE -> R.string.error_latitude_range
            }
            CoordinateField.LONGITUDE -> when (error.error) {
                CoordinateError.REQUIRED -> R.string.error_longitude_required
                CoordinateError.NOT_A_NUMBER -> R.string.error_longitude_number
                CoordinateError.OUT_OF_RANGE -> R.string.error_longitude_range
            }
        }
        return getString(resource)
    }

    private fun setStatus(status: String, message: String? = null) {
        currentStatus = status
        statusMessage = message
        renderStatus()
    }

    private fun renderStatus() {
        val statusLabel = when (currentStatus) {
            MockLocationService.STATUS_ACTIVE -> {
                val coordinates = activeCoordinates ?: settings.loadActiveCoordinates()
                if (coordinates == null) {
                    getString(R.string.status_inactive)
                } else {
                    getString(
                        R.string.status_active,
                        formatCoordinate(coordinates.latitude),
                        formatCoordinate(coordinates.longitude),
                        getString(if (activeJitter) R.string.enabled else R.string.disabled),
                    )
                }
            }
            MockLocationService.STATUS_STARTING -> getString(R.string.status_starting)
            MockLocationService.STATUS_STOPPING -> getString(R.string.status_stopping)
            MockLocationService.STATUS_PERMISSION_REQUIRED -> getString(R.string.status_permission_required)
            MockLocationService.STATUS_ERROR -> getString(R.string.status_error)
            else -> getString(R.string.status_inactive)
        }
        statusText.text = if (statusMessage.isNullOrBlank()) statusLabel else "$statusLabel · $statusMessage"
        val color = when (currentStatus) {
            MockLocationService.STATUS_ACTIVE -> getColor(R.color.success)
            MockLocationService.STATUS_STARTING,
            MockLocationService.STATUS_STOPPING,
            MockLocationService.STATUS_PERMISSION_REQUIRED -> getColor(R.color.warning)
            MockLocationService.STATUS_ERROR -> getColor(R.color.danger)
            else -> getColor(R.color.ink)
        }
        statusText.setTextColor(color)
        renderButtons()
    }

    private fun renderButtons() {
        val busy = currentStatus == MockLocationService.STATUS_STARTING ||
            currentStatus == MockLocationService.STATUS_STOPPING
        applyButton.isEnabled = !busy
        stopButton.isEnabled = currentStatus == MockLocationService.STATUS_ACTIVE ||
            currentStatus == MockLocationService.STATUS_STARTING
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_title)
            .setMessage(R.string.permission_denied_body)
            .setPositiveButton(R.string.open_app_settings) { _, _ ->
                openSettings(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", packageName, null)),
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: RuntimeException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.rootContainer)
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, windowInsets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemInsets = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                left = systemInsets.left
                top = systemInsets.top
                right = systemInsets.right
                bottom = systemInsets.bottom
            } else {
                left = windowInsets.systemWindowInsetLeft
                top = windowInsets.systemWindowInsetTop
                right = windowInsets.systemWindowInsetRight
                bottom = windowInsets.systemWindowInsetBottom
            }
            view.setPadding(
                initialLeft + left,
                initialTop + top,
                initialRight + right,
                initialBottom + bottom,
            )
            windowInsets
        }
        root.requestApplyInsets()
    }

    private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

    private companion object {
        const val PERMISSION_REQUEST_CODE = 4101
    }
}
