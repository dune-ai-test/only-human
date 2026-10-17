package com.only.human.design.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn icon set in the SF Symbols spirit — round caps, thin geometric
 * strokes, 24dp grid. Deliberately not Material icons: they are the single
 * biggest "this is a Google app" tell.
 */
object OhIcons {

    val Home = strokeIcon("Home") {
        stroke("M4.6,11.5 L12,4.9 L19.4,11.5")
        stroke("M6.7,10.0 L6.7,19.1 L17.3,19.1 L17.3,10.0")
    }

    val Search = strokeIcon("Search") {
        stroke(circle(10.8f, 10.8f, 5.6f))
        stroke("M15.0,15.0 L19.4,19.4")
    }

    val Plus = strokeIcon("Plus") {
        stroke("M12,5.6 L12,18.4 M5.6,12 L18.4,12", width = 1.9f)
    }

    val Bell = strokeIcon("Bell") {
        stroke("M12,4.9 C9.0,4.9 6.7,7.2 6.7,10.3 L6.7,13.4 L5.1,16.4 L18.9,16.4 L17.3,13.4 L17.3,10.3 C17.3,7.2 15.0,4.9 12,4.9 Z")
        stroke("M10.3,18.6 C10.6,19.5 11.2,20.0 12,20.0 C12.8,20.0 13.4,19.5 13.7,18.6")
    }

    val Person = strokeIcon("Person") {
        stroke(circle(12f, 8.4f, 3.5f))
        stroke("M5.7,19.3 C5.7,16.0 8.4,13.6 12,13.6 C15.6,13.6 18.3,16.0 18.3,19.3")
    }

    private const val HEART =
        "M12,19.9 C8.6,17.3 4.7,14.0 4.7,9.9 C4.7,7.2 6.7,5.3 9.1,5.3 C10.7,5.3 11.6,6.2 12,7.0 " +
            "C12.4,6.2 13.3,5.3 14.9,5.3 C17.3,5.3 19.3,7.2 19.3,9.9 C19.3,14.0 15.4,17.3 12,19.9 Z"

    val Heart = strokeIcon("Heart") { stroke(HEART) }

    val HeartFill = fillIcon("HeartFill") { fill(HEART) }

    val Bubble = strokeIcon("Bubble") {
        stroke(
            "M7.0,5.6 L17.0,5.6 A3.2,3.2 0 0 1 20.2,8.8 L20.2,13.2 A3.2,3.2 0 0 1 17.0,16.4 " +
                "L11.9,16.4 L7.9,19.7 L7.9,16.4 L7.0,16.4 A3.2,3.2 0 0 1 3.8,13.2 L3.8,8.8 " +
                "A3.2,3.2 0 0 1 7.0,5.6 Z",
            width = 1.6f,
        )
    }

    val Share = strokeIcon("Share") {
        stroke("M6.3,11.3 L6.3,17.4 A2.0,2.0 0 0 0 8.3,19.4 L15.7,19.4 A2.0,2.0 0 0 0 17.7,17.4 L17.7,11.3")
        stroke("M12,14.4 L12,4.8 M12,4.8 L8.4,8.4 M12,4.8 L15.6,8.4")
    }

    val ChevronLeft = strokeIcon("ChevronLeft") {
        stroke("M14.4,6.0 L8.5,12.0 L14.4,18.0", width = 1.9f)
    }

    val ChevronRight = strokeIcon("ChevronRight") {
        stroke("M9.6,6.0 L15.5,12.0 L9.6,18.0", width = 1.9f)
    }

    val Xmark = strokeIcon("Xmark") {
        stroke("M6.7,6.7 L17.3,17.3 M17.3,6.7 L6.7,17.3", width = 1.9f)
    }

    val Check = strokeIcon("Check") {
        stroke("M5.7,12.7 L9.9,16.9 L18.3,7.7", width = 2.0f)
    }

    val Ellipsis = fillIcon("Ellipsis") {
        fill(circle(6f, 12f, 1.5f))
        fill(circle(12f, 12f, 1.5f))
        fill(circle(18f, 12f, 1.5f))
    }
}

// ---------------------------------------------------------------------------
// Path helpers
// ---------------------------------------------------------------------------

private fun circle(cx: Float, cy: Float, r: Float): String {
    val d = r * 2f
    return "M$cx,$cy m-${r},0 a$r,$r 0 1,0 $d,0 a$r,$r 0 1,0 -${d},0"
}

private fun strokeIcon(name: String, block: IconBuilder.() -> Unit): ImageVector =
    buildIcon(name, block)

private fun fillIcon(name: String, block: IconBuilder.() -> Unit): ImageVector =
    buildIcon(name, block)

private class IconBuilder(val builder: ImageVector.Builder) {
    fun stroke(d: String, width: Float = 1.7f) {
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    fun fill(d: String) {
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }
}

private fun buildIcon(name: String, block: IconBuilder.() -> Unit): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    IconBuilder(builder).block()
    return builder.build()
}
