package ru.nksk.lctapp.feature.map.ui

import androidx.compose.ui.geometry.Rect

// Approximate landmark bounds in the full map image, independent of progression.
internal val mapLocations = listOf(
    MapLocationUi("observatory", "Обсерватория", Rect(.425f, .076f, .583f, .155f)),
    MapLocationUi("workshop", "Мастерская", Rect(.058f, .203f, .391f, .299f)),
    MapLocationUi("gates", "Ворота", Rect(.703f, .224f, .947f, .303f)),
    MapLocationUi("windmill", "Мельница", Rect(.144f, .350f, .300f, .425f)),
    MapLocationUi("hill", "Холм", Rect(.682f, .366f, .985f, .476f)),
    MapLocationUi("trail", "Тропа", Rect(.125f, .535f, .338f, .637f)),
    MapLocationUi("fair", "Ярмарка", Rect(.650f, .524f, .983f, .633f)),
    MapLocationUi("city", "Город", Rect(.016f, .700f, .475f, .821f)),
    MapLocationUi("pier", "Причал", Rect(.591f, .765f, .890f, .858f)),
)
