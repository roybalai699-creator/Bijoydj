package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight bundled Material-style vector icons replacing material-icons-extended
 * to keep APK size compact and builds fast.
 */
object BijoyIcons {

    private inline fun icon(
        name: String,
        crossinline block: ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

    val Pause: ImageVector by lazy {
        icon("Pause") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f); horizontalLineTo(10f); verticalLineTo(5f); horizontalLineTo(6f); close()
                moveTo(14f, 5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(5f); close()
            }
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f); horizontalLineTo(18f); verticalLineTo(18f); horizontalLineTo(6f); close()
            }
        }
    }

    val FastForward: ImageVector by lazy {
        icon("FastForward") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 18f); lineTo(12.5f, 12f); lineTo(4f, 6f); close()
                moveTo(13f, 6f); verticalLineTo(18f); lineTo(21.5f, 12f); close()
            }
        }
    }

    val FiberManualRecord: ImageVector by lazy {
        icon("FiberManualRecord") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 4f)
                curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
                reflectiveCurveTo(7.58f, 20f, 12f, 20f)
                reflectiveCurveTo(20f, 16.42f, 20f, 12f)
                reflectiveCurveTo(16.42f, 4f, 12f, 4f)
                close()
            }
        }
    }

    val RestartAlt: ImageVector by lazy {
        icon("RestartAlt") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 5f)
                curveTo(8.13f, 5f, 5f, 8.13f, 5f, 12f)
                curveTo(5f, 15.87f, 8.13f, 19f, 12f, 19f)
                curveTo(15.87f, 19f, 19f, 15.87f, 19f, 12f)
                moveTo(12f, 2f); lineTo(9f, 5f); lineTo(12f, 8f)
            }
        }
    }

    val Repeat: ImageVector by lazy {
        icon("Repeat") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 7f); horizontalLineTo(17f); verticalLineTo(10f); lineTo(21f, 6f); lineTo(17f, 2f); verticalLineTo(5f); horizontalLineTo(5f); verticalLineTo(11f); horizontalLineTo(7f); close()
                moveTo(17f, 17f); horizontalLineTo(7f); verticalLineTo(14f); lineTo(3f, 18f); lineTo(7f, 22f); verticalLineTo(19f); horizontalLineTo(19f); verticalLineTo(13f); horizontalLineTo(17f); close()
            }
        }
    }

    val SwapHoriz: ImageVector by lazy {
        icon("SwapHoriz") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.99f, 11f); lineTo(3f, 15f); lineTo(6.99f, 19f); verticalLineTo(16f); horizontalLineTo(14f); verticalLineTo(14f); horizontalLineTo(6.99f); close()
                moveTo(21f, 9f); lineTo(17.01f, 5f); verticalLineTo(8f); horizontalLineTo(10f); verticalLineTo(10f); horizontalLineTo(17.01f); verticalLineTo(13f); close()
            }
        }
    }

    val GraphicEq: ImageVector by lazy {
        icon("GraphicEq") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 18f); horizontalLineTo(9f); verticalLineTo(6f); horizontalLineTo(7f); close()
                moveTo(11f, 22f); horizontalLineTo(13f); verticalLineTo(2f); horizontalLineTo(11f); close()
                moveTo(3f, 14f); horizontalLineTo(5f); verticalLineTo(10f); horizontalLineTo(3f); close()
                moveTo(15f, 18f); horizontalLineTo(17f); verticalLineTo(6f); horizontalLineTo(15f); close()
                moveTo(19f, 14f); horizontalLineTo(21f); verticalLineTo(10f); horizontalLineTo(19f); close()
            }
        }
    }

    val Equalizer: ImageVector by lazy {
        icon("Equalizer") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 20f); horizontalLineTo(14f); verticalLineTo(4f); horizontalLineTo(10f); close()
                moveTo(4f, 20f); horizontalLineTo(8f); verticalLineTo(12f); horizontalLineTo(4f); close()
                moveTo(16f, 9f); verticalLineTo(20f); horizontalLineTo(20f); verticalLineTo(9f); close()
            }
        }
    }

    val Tune: ImageVector by lazy {
        icon("Tune") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 17f); verticalLineTo(19f); horizontalLineTo(9f); verticalLineTo(17f); close()
                moveTo(3f, 5f); verticalLineTo(7f); horizontalLineTo(13f); verticalLineTo(5f); close()
                moveTo(13f, 21f); verticalLineTo(19f); horizontalLineTo(21f); verticalLineTo(17f); horizontalLineTo(13f); verticalLineTo(15f); horizontalLineTo(11f); verticalLineTo(21f); close()
                moveTo(7f, 9f); verticalLineTo(11f); horizontalLineTo(3f); verticalLineTo(13f); horizontalLineTo(7f); verticalLineTo(15f); horizontalLineTo(9f); verticalLineTo(9f); close()
                moveTo(21f, 13f); verticalLineTo(11f); horizontalLineTo(11f); verticalLineTo(13f); close()
                moveTo(15f, 9f); horizontalLineTo(17f); verticalLineTo(7f); horizontalLineTo(21f); verticalLineTo(5f); horizontalLineTo(17f); verticalLineTo(3f); horizontalLineTo(15f); close()
            }
        }
    }

    val MusicNote: ImageVector by lazy {
        icon("MusicNote") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f); verticalLineTo(13.55f)
                curveTo(11.41f, 13.21f, 10.73f, 13f, 10f, 13f)
                curveTo(7.79f, 13f, 6f, 14.79f, 6f, 17f)
                reflectiveCurveTo(7.79f, 21f, 10f, 21f)
                reflectiveCurveTo(14f, 19.21f, 14f, 17f)
                verticalLineTo(7f); horizontalLineTo(18f); verticalLineTo(3f); close()
            }
        }
    }

    val Audiotrack: ImageVector by lazy { MusicNote }

    val AudioFile: ImageVector by lazy {
        icon("AudioFile") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 2f); horizontalLineTo(6f); verticalLineTo(22f); horizontalLineTo(18f); verticalLineTo(8f); close()
                moveTo(13f, 9f); verticalLineTo(3.5f); lineTo(18.5f, 9f); close()
            }
        }
    }

    val LibraryMusic: ImageVector by lazy {
        icon("LibraryMusic") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 2f); horizontalLineTo(8f); verticalLineTo(16f); horizontalLineTo(20f); close()
                moveTo(17f, 9f); horizontalLineTo(14f); verticalLineTo(13.5f)
                curveTo(14f, 14.33f, 13.33f, 15f, 12.5f, 15f)
                reflectiveCurveTo(11f, 14.33f, 11f, 13.5f)
                reflectiveCurveTo(11.67f, 12f, 12.5f, 12f); horizontalLineTo(13f); verticalLineTo(7f); horizontalLineTo(17f); close()
                moveTo(4f, 6f); horizontalLineTo(2f); verticalLineTo(22f); horizontalLineTo(18f); verticalLineTo(20f); horizontalLineTo(4f); close()
            }
        }
    }

    val QueueMusic: ImageVector by lazy {
        icon("QueueMusic") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 6f); horizontalLineTo(3f); verticalLineTo(8f); horizontalLineTo(15f); close()
                moveTo(15f, 10f); horizontalLineTo(3f); verticalLineTo(12f); horizontalLineTo(15f); close()
                moveTo(3f, 16f); horizontalLineTo(11f); verticalLineTo(14f); horizontalLineTo(3f); close()
                moveTo(17f, 6f); verticalLineTo(14.18f)
                curveTo(16.69f, 14.07f, 16.35f, 14f, 16f, 14f)
                curveTo(14.34f, 14f, 13f, 15.34f, 13f, 17f)
                reflectiveCurveTo(14.34f, 20f, 16f, 20f)
                reflectiveCurveTo(19f, 18.66f, 19f, 17f)
                verticalLineTo(8f); horizontalLineTo(22f); verticalLineTo(6f); close()
            }
        }
    }

    val PlaylistAdd: ImageVector by lazy {
        icon("PlaylistAdd") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 10f); horizontalLineTo(3f); verticalLineTo(12f); horizontalLineTo(14f); close()
                moveTo(14f, 6f); horizontalLineTo(3f); verticalLineTo(8f); horizontalLineTo(14f); close()
                moveTo(18f, 14f); verticalLineTo(10f); horizontalLineTo(16f); verticalLineTo(14f); horizontalLineTo(12f); verticalLineTo(16f); horizontalLineTo(16f); verticalLineTo(20f); horizontalLineTo(18f); verticalLineTo(16f); horizontalLineTo(22f); verticalLineTo(14f); close()
                moveTo(3f, 16f); horizontalLineTo(10f); verticalLineTo(14f); horizontalLineTo(3f); close()
            }
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 4f); horizontalLineTo(4f); verticalLineTo(20f); horizontalLineTo(20f); verticalLineTo(6f); horizontalLineTo(12f); close()
            }
        }
    }

    val CreateNewFolder: ImageVector by lazy {
        icon("CreateNewFolder") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 6f); horizontalLineTo(12f); lineTo(10f, 4f); horizontalLineTo(4f); verticalLineTo(20f); horizontalLineTo(20f); close()
                moveTo(19f, 14f); horizontalLineTo(16f); verticalLineTo(17f); horizontalLineTo(14f); verticalLineTo(14f); horizontalLineTo(11f); verticalLineTo(12f); horizontalLineTo(14f); verticalLineTo(9f); horizontalLineTo(16f); verticalLineTo(12f); horizontalLineTo(19f); close()
            }
        }
    }

    val Album: ImageVector by lazy {
        icon("Album") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveTo(6.48f, 22f, 12f, 22f)
                reflectiveCurveTo(22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f); close()
                moveTo(12f, 14.5f)
                curveTo(10.62f, 14.5f, 9.5f, 13.38f, 9.5f, 12f)
                reflectiveCurveTo(10.62f, 9.5f, 12f, 9.5f)
                reflectiveCurveTo(14.5f, 10.62f, 14.5f, 12f)
                reflectiveCurveTo(13.38f, 14.5f, 12f, 14.5f); close()
            }
        }
    }

    val Cloud: ImageVector by lazy {
        icon("Cloud") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
                horizontalLineTo(19f)
                curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
                curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f); close()
            }
        }
    }

    val CloudQueue: ImageVector by lazy { Cloud }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 14f)
                curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
                verticalLineTo(5f)
                curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
                reflectiveCurveTo(9f, 3.34f, 9f, 5f)
                verticalLineTo(11f)
                curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f); close()
                moveTo(17f, 11f)
                curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
                reflectiveCurveTo(7f, 13.76f, 7f, 11f)
                horizontalLineTo(5f)
                curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.92f)
                verticalLineTo(21f); horizontalLineTo(13f); verticalLineTo(17.92f)
                curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f); close()
            }
        }
    }

    val History: ImageVector by lazy {
        icon("History") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 8f); verticalLineTo(12f); lineTo(15f, 14f)
                moveTo(4f, 12f); curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
                curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
                curveTo(20f, 7.58f, 16.42f, 4f, 12f, 4f)
                curveTo(9f, 4f, 6.4f, 5.6f, 5f, 8f)
            }
        }
    }

    val Schedule: ImageVector by lazy { History }

    val GridView: ImageVector by lazy {
        icon("GridView") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 3f); horizontalLineTo(11f); verticalLineTo(11f); horizontalLineTo(3f); close()
                moveTo(13f, 3f); horizontalLineTo(21f); verticalLineTo(11f); horizontalLineTo(13f); close()
                moveTo(3f, 13f); horizontalLineTo(11f); verticalLineTo(21f); horizontalLineTo(3f); close()
                moveTo(13f, 13f); horizontalLineTo(21f); verticalLineTo(21f); horizontalLineTo(13f); close()
            }
        }
    }

    val DashboardCustomize: ImageVector by lazy { GridView }

    val Layers: ImageVector by lazy {
        icon("Layers") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.99f, 18.54f); lineTo(4.62f, 12.81f); lineTo(3f, 14.07f); lineTo(12f, 21.07f); lineTo(21f, 14.07f); lineTo(19.37f, 12.8f); close()
                moveTo(12f, 16f); lineTo(19.36f, 10.27f); lineTo(21f, 9f); lineTo(12f, 2f); lineTo(3f, 9f); lineTo(4.63f, 10.27f); close()
            }
        }
    }

    val AutoAwesome: ImageVector by lazy {
        icon("AutoAwesome") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 9f); lineTo(20.25f, 6.25f); lineTo(23f, 5f); lineTo(20.25f, 3.75f); lineTo(19f, 1f); lineTo(17.75f, 3.75f); lineTo(15f, 5f); lineTo(17.75f, 6.25f); close()
                moveTo(11.5f, 9.5f); lineTo(9f, 4f); lineTo(6.5f, 9.5f); lineTo(1f, 12f); lineTo(6.5f, 14.5f); lineTo(9f, 20f); lineTo(11.5f, 14.5f); lineTo(17f, 12f); close()
            }
        }
    }

    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 21f); horizontalLineTo(10f); lineTo(11f, 14f); horizontalLineTo(7.5f); lineTo(13f, 3f); horizontalLineTo(14f); lineTo(13f, 10f); horizontalLineTo(16.5f); close()
            }
        }
    }

    val FlashOn: ImageVector by lazy { Bolt }

    val SmartToy: ImageVector by lazy {
        icon("SmartToy") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 9f); verticalLineTo(7f); horizontalLineTo(13f); verticalLineTo(4f); horizontalLineTo(11f); verticalLineTo(7f); horizontalLineTo(4f); verticalLineTo(9f); horizontalLineTo(2f); verticalLineTo(15f); horizontalLineTo(4f); verticalLineTo(19f); horizontalLineTo(20f); verticalLineTo(15f); horizontalLineTo(22f); verticalLineTo(9f); close()
                moveTo(9f, 14f); curveTo(8.17f, 14f, 7.5f, 13.33f, 7.5f, 12.5f); reflectiveCurveTo(8.17f, 11f, 9f, 11f); reflectiveCurveTo(10.5f, 11.67f, 10.5f, 12.5f); reflectiveCurveTo(9.83f, 14f, 9f, 14f); close()
                moveTo(15f, 14f); curveTo(14.17f, 14f, 13.5f, 13.33f, 13.5f, 12.5f); reflectiveCurveTo(14.17f, 11f, 15f, 11f); reflectiveCurveTo(16.5f, 11.67f, 16.5f, 12.5f); reflectiveCurveTo(15.83f, 14f, 15f, 14f); close()
            }
        }
    }

    val Assistant: ImageVector by lazy { AutoAwesome }
    val Pattern: ImageVector by lazy { GridView }

    val Analytics: ImageVector by lazy {
        icon("Analytics") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 3f); horizontalLineTo(5f); verticalLineTo(21f); horizontalLineTo(19f); close()
                moveTo(9f, 17f); horizontalLineTo(7f); verticalLineTo(12f); horizontalLineTo(9f); close()
                moveTo(13f, 17f); horizontalLineTo(11f); verticalLineTo(7f); horizontalLineTo(13f); close()
                moveTo(17f, 17f); horizontalLineTo(15f); verticalLineTo(10f); horizontalLineTo(17f); close()
            }
        }
    }

    val MultilineChart: ImageVector by lazy {
        icon("MultilineChart") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 17f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(21f, 7f)
            }
        }
    }

    val TrendingUp: ImageVector by lazy {
        icon("TrendingUp") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 6f); lineTo(18.29f, 8.29f); lineTo(13.41f, 13.17f); lineTo(9.41f, 9.17f); lineTo(2f, 16.59f); lineTo(3.41f, 18f); lineTo(9.41f, 12f); lineTo(13.41f, 16f); lineTo(19.71f, 9.71f); lineTo(22f, 12f); verticalLineTo(6f); close()
            }
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.41f, 7.41f); lineTo(14f, 6f); lineTo(8f, 12f); lineTo(14f, 18f); lineTo(15.41f, 16.59f); lineTo(10.83f, 12f); close()
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 6f); lineTo(8.59f, 7.41f); lineTo(13.17f, 12f); lineTo(8.59f, 16.59f); lineTo(10f, 18f); lineTo(16f, 12f); close()
            }
        }
    }

    val ArrowUpward: ImageVector by lazy {
        icon("ArrowUpward") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 12f); lineTo(5.41f, 13.41f); lineTo(11f, 7.83f); verticalLineTo(20f); horizontalLineTo(13f); verticalLineTo(7.83f); lineTo(18.58f, 13.42f); lineTo(20f, 12f); lineTo(12f, 4f); close()
            }
        }
    }

    val ArrowDownward: ImageVector by lazy {
        icon("ArrowDownward") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 12f); lineTo(18.59f, 10.59f); lineTo(13f, 16.17f); verticalLineTo(4f); horizontalLineTo(11f); verticalLineTo(16.17f); lineTo(5.42f, 10.58f); lineTo(4f, 12f); lineTo(12f, 20f); close()
            }
        }
    }

    val OpenInFull: ImageVector by lazy {
        icon("OpenInFull") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(21f, 11f); verticalLineTo(3f); horizontalLineTo(13f); lineTo(16.29f, 6.29f); lineTo(6.29f, 16.29f); lineTo(3f, 13f); verticalLineTo(21f); horizontalLineTo(11f); lineTo(7.71f, 17.71f); lineTo(17.71f, 7.71f); close()
            }
        }
    }

    val CloseFullscreen: ImageVector by lazy {
        icon("CloseFullscreen") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(22f, 3.41f); lineTo(16.71f, 8.7f); lineTo(20f, 12f); horizontalLineTo(12f); verticalLineTo(4f); lineTo(15.29f, 7.29f); lineTo(20.59f, 2f); close()
                moveTo(3.41f, 22f); lineTo(8.7f, 16.71f); lineTo(12f, 20f); verticalLineTo(12f); horizontalLineTo(4f); lineTo(7.29f, 15.29f); lineTo(2f, 20.59f); close()
            }
        }
    }

    val CheckCircleOutline: ImageVector by lazy {
        icon("CheckCircleOutline") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f); curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f); curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f); curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f); curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f); close()
                moveTo(8f, 12.5f); lineTo(10.8f, 15.2f); lineTo(16.2f, 9.5f)
            }
        }
    }

    val ErrorOutline: ImageVector by lazy {
        icon("ErrorOutline") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f); curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f); curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f); curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f); curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f); close()
                moveTo(12f, 7.5f); verticalLineTo(13f)
                moveTo(12f, 16.2f); verticalLineTo(16.5f)
            }
        }
    }

    val DeleteOutline: ImageVector by lazy {
        icon("DeleteOutline") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f); curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f); horizontalLineTo(16f); curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f); verticalLineTo(7f); horizontalLineTo(6f); close()
                moveTo(8f, 9f); horizontalLineTo(16f); verticalLineTo(19f); horizontalLineTo(8f); close()
                moveTo(15.5f, 4f); lineTo(14.5f, 3f); horizontalLineTo(9.5f); lineTo(8.5f, 4f); horizontalLineTo(5f); verticalLineTo(6f); horizontalLineTo(19f); verticalLineTo(4f); close()
            }
        }
    }

    val DeleteSweep: ImageVector by lazy { DeleteOutline }

    val StarBorder: ImageVector by lazy {
        icon("StarBorder") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f); lineTo(14.8f, 8.8f); lineTo(21f, 9.6f); lineTo(16.5f, 14f); lineTo(17.6f, 20.2f); lineTo(12f, 17.2f); lineTo(6.4f, 20.2f); lineTo(7.5f, 14f); lineTo(3f, 9.6f); lineTo(9.2f, 8.8f); close()
            }
        }
    }

    val FileUpload: ImageVector by lazy {
        icon("FileUpload") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 16f); horizontalLineTo(15f); verticalLineTo(10f); horizontalLineTo(19f); lineTo(12f, 3f); lineTo(5f, 10f); horizontalLineTo(9f); close()
                moveTo(5f, 18f); horizontalLineTo(19f); verticalLineTo(20f); horizontalLineTo(5f); close()
            }
        }
    }

    val SaveAlt: ImageVector by lazy {
        icon("SaveAlt") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 12f); verticalLineTo(19f); horizontalLineTo(5f); verticalLineTo(12f); horizontalLineTo(3f); verticalLineTo(19f); curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f); horizontalLineTo(19f); curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f); verticalLineTo(12f); close()
                moveTo(13f, 12.67f); lineTo(15.59f, 10.09f); lineTo(17f, 11.5f); lineTo(12f, 16.5f); lineTo(7f, 11.5f); lineTo(8.41f, 10.09f); lineTo(11f, 12.67f); verticalLineTo(3f); horizontalLineTo(13f); close()
            }
        }
    }

    val VpnKey: ImageVector by lazy {
        icon("VpnKey") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.65f, 10f)
                curveTo(11.83f, 7.67f, 9.61f, 6f, 7f, 6f)
                curveTo(3.69f, 6f, 1f, 8.69f, 1f, 12f)
                reflectiveCurveTo(3.69f, 18f, 7f, 18f)
                curveTo(9.61f, 18f, 11.83f, 16.33f, 12.65f, 14f)
                horizontalLineTo(17f); verticalLineTo(18f); horizontalLineTo(21f); verticalLineTo(14f); horizontalLineTo(23f); verticalLineTo(10f); close()
                moveTo(7f, 14f); curveTo(5.9f, 14f, 5f, 13.1f, 5f, 12f); reflectiveCurveTo(5.9f, 10f, 7f, 10f); reflectiveCurveTo(9f, 10.9f, 9f, 12f); reflectiveCurveTo(8.1f, 14f, 7f, 14f); close()
            }
        }
    }

    val Campaign: ImageVector by lazy {
        icon("Campaign") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 11f); verticalLineTo(13f); horizontalLineTo(22f); verticalLineTo(11f); close()
                moveTo(16f, 17.61f); lineTo(19.2f, 20f); lineTo(20.4f, 18.4f); lineTo(17.2f, 16f); close()
                moveTo(20.4f, 5.6f); lineTo(19.2f, 4f); lineTo(16f, 6.4f); lineTo(17.2f, 8f); close()
                moveTo(4f, 9f); curveTo(2.9f, 9f, 2f, 9.9f, 2f, 11f); verticalLineTo(13f); curveTo(2f, 14.1f, 2.9f, 15f, 4f, 15f); horizontalLineTo(5f); verticalLineTo(19f); horizontalLineTo(7f); verticalLineTo(15f); horizontalLineTo(8f); lineTo(13f, 18f); verticalLineTo(6f); lineTo(8f, 9f); close()
            }
        }
    }

    val GpsFixed: ImageVector by lazy {
        icon("GpsFixed") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 5f); curveTo(15.87f, 5f, 19f, 8.13f, 19f, 12f); curveTo(19f, 15.87f, 15.87f, 19f, 12f, 19f); curveTo(8.13f, 19f, 5f, 15.87f, 5f, 12f); curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f); close()
                moveTo(12f, 2f); verticalLineTo(5f)
                moveTo(12f, 19f); verticalLineTo(22f)
                moveTo(2f, 12f); horizontalLineTo(5f)
                moveTo(19f, 12f); horizontalLineTo(22f)
            }
        }
    }

    val NotificationsActive: ImageVector by lazy {
        icon("NotificationsActive") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 22f); curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f); horizontalLineTo(10f); curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f); close()
                moveTo(18f, 16f); verticalLineTo(11f); curveTo(18f, 7.93f, 16.37f, 5.36f, 13.5f, 4.68f); verticalLineTo(4f); curveTo(13.5f, 3.17f, 12.83f, 2.5f, 12f, 2.5f); reflectiveCurveTo(10.5f, 3.17f, 10.5f, 4f); verticalLineTo(4.68f); curveTo(7.64f, 5.36f, 6f, 7.92f, 6f, 11f); verticalLineTo(16f); lineTo(4f, 18f); verticalLineTo(19f); horizontalLineTo(20f); verticalLineTo(18f); close()
            }
        }
    }

    val Alarm: ImageVector by lazy { NotificationsActive }

    val SportsMma: ImageVector by lazy {
        icon("SportsMma") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 20f); horizontalLineTo(17f); verticalLineTo(17f); horizontalLineTo(7f); close()
                moveTo(18f, 7f); horizontalLineTo(17f); verticalLineTo(6f); curveTo(17f, 4.34f, 15.66f, 3f, 14f, 3f); horizontalLineTo(8f); curveTo(6.34f, 3f, 5f, 4.34f, 5f, 6f); verticalLineTo(12f); curveTo(5f, 14.21f, 6.79f, 16f, 9f, 16f); horizontalLineTo(15f); curveTo(17.21f, 16f, 19f, 14.21f, 19f, 12f); verticalLineTo(8f); curveTo(19f, 7.45f, 18.55f, 7f, 18f, 7f); close()
            }
        }
    }

    val Groups: ImageVector by lazy {
        icon("Groups") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12.75f); curveTo(9.66f, 12.75f, 5f, 13.92f, 5f, 16.25f); verticalLineTo(18f); horizontalLineTo(19f); verticalLineTo(16.25f); curveTo(19f, 13.92f, 14.34f, 12.75f, 12f, 12.75f); close()
                moveTo(12f, 11f); curveTo(13.93f, 11f, 15.5f, 9.43f, 15.5f, 7.5f); reflectiveCurveTo(13.93f, 4f, 12f, 4f); reflectiveCurveTo(8.5f, 5.57f, 8.5f, 7.5f); reflectiveCurveTo(10.07f, 11f, 12f, 11f); close()
            }
        }
    }

    val PanTool: ImageVector by lazy {
        icon("PanTool") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 3f); horizontalLineTo(12f); verticalLineTo(12f); horizontalLineTo(10f); close()
                moveTo(14f, 4f); horizontalLineTo(16f); verticalLineTo(12f); horizontalLineTo(14f); close()
                moveTo(6f, 5f); horizontalLineTo(8f); verticalLineTo(13f); horizontalLineTo(6f); close()
                moveTo(18f, 7f); horizontalLineTo(20f); verticalLineTo(14f); curveTo(20f, 18.42f, 16.42f, 22f, 12f, 22f); curveTo(8.69f, 22f, 5.84f, 19.99f, 4.6f, 17.12f); lineTo(3.2f, 12.8f); lineTo(5f, 12f); lineTo(6f, 14f); horizontalLineTo(18f); close()
            }
        }
    }

    val RecordVoiceOver: ImageVector by lazy {
        icon("RecordVoiceOver") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 13f); curveTo(11.21f, 13f, 13f, 11.21f, 13f, 9f); reflectiveCurveTo(11.21f, 5f, 9f, 5f); reflectiveCurveTo(5f, 6.79f, 5f, 9f); reflectiveCurveTo(6.79f, 13f, 9f, 13f); close()
                moveTo(9f, 15f); curveTo(6.33f, 15f, 1f, 16.34f, 1f, 19f); verticalLineTo(21f); horizontalLineTo(17f); verticalLineTo(19f); curveTo(17f, 16.34f, 11.67f, 15f, 9f, 15f); close()
                moveTo(16.76f, 5.36f); lineTo(15.08f, 7.05f); curveTo(16.02f, 8.21f, 16.02f, 9.88f, 15.08f, 11.05f); lineTo(16.76f, 12.74f); curveTo(18.67f, 10.67f, 18.67f, 7.42f, 16.76f, 5.36f); close()
            }
        }
    }
}
