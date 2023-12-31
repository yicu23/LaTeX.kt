// TODO: support for Set operations, convert some symbols to custom fonts such as \int, \sum
// Since last update:
// (please update strings.xml)
// Fixed \{, \langle, etc.
// Support for \iint, \oiint, \Iint, etc.
// Added \cdot, \ldots, \cdots, \vdots, \ddots

/* The following is useful that I just keep it for reference later

This lets you chain a modifier in a conditional block like this:
fun Modifier.conditional(condition : Boolean, modifier : Modifier.() -> Modifier) : Modifier {
    return if (condition) {
        then(modifier(Modifier))
    } else {
        this
    }
}

val applySpecialBackground : Boolean = [...]
Column(
    modifier = Modifier
        .fillMaxWidth()
        .conditional(applySpecialBackground) {
            background(Color.Red)
        }
        .padding(16.dp)

) { [...] }

The following code is applied to Text modifier to decorate after painting
Note that you have to use onTextLayout to apply changes in values in textBounds to draw correctly.
.drawBehind {
    drawLine(
        color = Color.Black,
        start = textBounds.topLeft,
        end = textBounds.topRight,
        strokeWidth = 3f
    )
    drawLine(
        color = Color.Black,
        start = textBounds.bottomLeft,
        end = textBounds.bottomRight,
        strokeWidth = 3f
    )
},

You can use content.apply() to display the contents in a Composable,
or leave a Composable without any code to display all contents.

 */

package com.example.fxcalculator

import androidx.annotation.ArrayRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import com.example.fxcalculator.LaTeXScope.Companion.attachMathMetaphor
import com.example.fxcalculator.LaTeXScope.Companion.parenthesis

private const val reduceFontFactor = 0.75f

private fun String.charAt(p: Int): Char? {
    return if (p >= 0 && p < this.length) this[p] else null
}

private fun Boolean.and(x: Int): Int {
    return if (this) x else 0
}

@Composable
private fun XmlArrayToMap(@ArrayRes arrayId: Int, map: MutableMap<String, String>) {
    val array = LocalContext.current.resources.getStringArray(arrayId)
    map.putAll(array.associateBy({ it.split('#')[0] }, { it.split('#')[1] }))
}

private val Command2Unicode = mutableMapOf<String, String>()

const val OverlineConst = 1
const val UnderlineConst = 2

private val CeilingLine = HorizontalAlignmentLine(
    merger = { old, new -> minOf(old, new) }
)
private val WaistLine = HorizontalAlignmentLine(
    merger = { old, new -> new }
)
private val FloorLine = HorizontalAlignmentLine(
    merger = { old, new -> maxOf(old, new) }
)
private val BasementLine = HorizontalAlignmentLine(
    merger = { old, new -> maxOf(old, new) }
)
private val HasDecorationLine = HorizontalAlignmentLine(
    merger = { old, new -> new }
)

private data class LaTeXAlignmentsOrProperties(
    val ceilingLine: Int,
    val waistLine: Int,
    val floorLine: Int,
    val basementLine: Int,
    val hasOverline: Boolean,
    val hasUnderline: Boolean
)

private fun Placeable.measureAlignment(alignmentLine: HorizontalAlignmentLine): Int {
    if (this[alignmentLine] != AlignmentLine.Unspecified) return this[alignmentLine]
    return when (alignmentLine) {
        CeilingLine -> (this[FirstBaseline] * 4 + 7) / 14
        WaistLine -> (this[FirstBaseline] * 9 + 7) / 14
        FloorLine -> this[FirstBaseline]
        BasementLine -> (this[FirstBaseline] * 18 + 7) / 14
        else -> AlignmentLine.Unspecified
    }
}

private fun Placeable.measureHasDecorationLine(decorationLineMask: Int): Boolean {
    val hasDecorationLine = this[HasDecorationLine]
    return hasDecorationLine != AlignmentLine.Unspecified
            && hasDecorationLine and decorationLineMask > 0
}

private fun Placeable.measureAlignmentsOrDecorationLines() = LaTeXAlignmentsOrProperties(
    this.measureAlignment(CeilingLine),
    this.measureAlignment(WaistLine),
    this.measureAlignment(FloorLine),
    this.measureAlignment(BasementLine),
    this.measureHasDecorationLine(OverlineConst),
    this.measureHasDecorationLine(UnderlineConst)
)

@Composable
fun LaTeX(
    laTeX: String,
    fontSize: TextUnit
) {
    // The Command2Unicode map has to be initialized inside a Composable,
    // so it is conveniently initialized inside the first LaTeX
    if (Command2Unicode.isEmpty()) {
        XmlArrayToMap(R.array.unicode, Command2Unicode)
    }

    ParseLaTeX.reset(text = laTeX)
    ParseLaTeX.Main(fontSize = fontSize)
    if (ParseLaTeX.status != ParseLaTeXStatus.EXIT) {
        // Todo(Error)
    }
}

@Composable
private fun LaTeX(
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    content: @Composable LaTeXScope.() -> Unit
) {
    val turtlePath = Path()

    Layout(
        content = { LaTeXScope.content() },
        modifier = modifier
            .drawBehind {
                drawPath(
                    path = turtlePath,
                    color = Color.Black,
                    style = Stroke(width = 4f)
                )
            }
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { measurable -> measurable.measure(looseConstraints) }

        fun Placeable.lastReferenceIndex(): Int {
            var i = placeables.indexOf(this) - 1
            while (i >= 0 && measurables[i].alignment != AlignmentMetaphor.UNSPECIFIED) {
                i--
            }
            return i
        }

        fun Placeable.nextReferenceIndex(): Int {
            var i = placeables.indexOf(this) + 1
            while (i < placeables.size && measurables[i].alignment != AlignmentMetaphor.UNSPECIFIED) {
                i++
            }
            return if (i < placeables.size) i else -1
        }

        /*
         * Calculate individual alignment lines
         */
        val ceilingLines = IntArray(placeables.size)
        val waistLines = IntArray(placeables.size)
        val floorLines = IntArray(placeables.size)
        val basementLines = IntArray(placeables.size)
        val hasOverlines = BooleanArray(placeables.size)
        val hasUnderlines = BooleanArray(placeables.size)

        placeables.forEachIndexed { index, placeable ->
            val measurable = measurables[index]
            var (ceilingLine, waistLine, floorLine, basementLine, hasOverline, hasUnderline)
                    = placeable.measureAlignmentsOrDecorationLines()

            if (measurable.alignment == AlignmentMetaphor.SUPERSCRIPT
                || measurable.alignment == AlignmentMetaphor.SUPERSCRIPT_
            ) {
                val referenceIndex = with(placeable) {
                    val lastRefIndex = lastReferenceIndex()
                    if (lastRefIndex < 0) nextReferenceIndex() else lastRefIndex
                }
                waistLine = if (referenceIndex < 0) {
                    basementLine
                } else if (measurables[referenceIndex].mathMetaphor == MathMetaphor.COLLECTIVEOP) {
                    waistLines[referenceIndex] - ceilingLines[referenceIndex] + basementLine
                } else if (waistLines[referenceIndex] - ceilingLines[referenceIndex] > basementLine / 2) {
                    waistLines[referenceIndex] - ceilingLines[referenceIndex] + basementLine / 2
                } else {
                    basementLine
                }
            }

            if (measurable.alignment == AlignmentMetaphor.SUBSCRIPT
                || measurable.alignment == AlignmentMetaphor.SUBSCRIPT_
            ) {
                val referenceIndex = with(placeable) {
                    val lastRefIndex = lastReferenceIndex()
                    if (lastRefIndex < 0) nextReferenceIndex() else lastRefIndex
                }
                waistLine = if (referenceIndex < 0) {
                    0
                } else if (measurables[referenceIndex].mathMetaphor == MathMetaphor.COLLECTIVEOP) {
                    waistLines[referenceIndex] - floorLines[referenceIndex]
                } else if (floorLines[referenceIndex] - waistLines[referenceIndex] > basementLine / 2) {
                    waistLines[referenceIndex] - floorLines[referenceIndex] + basementLine / 2
                } else {
                    0
                }
            }

            ceilingLines[index] = ceilingLine
            waistLines[index] = waistLine
            floorLines[index] = floorLine
            basementLines[index] = basementLine
            hasOverlines[index] = hasOverline
            hasUnderlines[index] = hasUnderline
        }

        /*
         * Adjust individual alignment lines and offsets.
         */
        val heightAboveWaistLine = waistLines.max()
        val heightBelowWaistLine = waistLines
            .mapIndexed { index, waistLine -> basementLines[index] - waistLine }
            .max()

        val mathMetaphorSpacing = mapOf(
            MathMetaphor.UNARYOP to 0,
            MathMetaphor.OP to fontSize.div(5).roundToPx(),
            MathMetaphor.COMPARATOR to fontSize.div(4).roundToPx(),
            MathMetaphor.FUNCTION to fontSize.div(12).roundToPx(),
            MathMetaphor.COLLECTIVEOP to 0,
            MathMetaphor.UNSPECIFIED to 0,
            null to 0
        )

        fun Placeable.calcSpacing(): Int {

            fun correctMathMetaphor(
                lastMathMeta: MathMetaphor?,
                nextMathMeta: MathMetaphor?
            ): MathMetaphor {
                if (lastMathMeta == null || nextMathMeta == null) return MathMetaphor.UNARYOP
                return if (lastMathMeta == MathMetaphor.UNSPECIFIED ||
                    nextMathMeta == MathMetaphor.OP ||
                    lastMathMeta == nextMathMeta
                ) MathMetaphor.OP else MathMetaphor.UNARYOP
            }

            var thisIndex = placeables.indexOf(this)
            if (measurables[thisIndex].alignment != AlignmentMetaphor.UNSPECIFIED) {
                thisIndex = this.lastReferenceIndex()
            }
            val thisMathMetaphor = if (thisIndex >= 0)
                measurables[thisIndex].mathMetaphor else null

            val nextIndex = this.nextReferenceIndex()
            val nextMathMetaphor = if (nextIndex >= 0)
                measurables[nextIndex].mathMetaphor else null

            val correctThisMathMeta = if (thisMathMetaphor == MathMetaphor.OP) {
                val lastIndex = if (thisIndex >= 0) {
                    placeables[thisIndex].lastReferenceIndex()
                } else -1
                val lastMathMetaphor = if (lastIndex >= 0)
                    measurables[lastIndex].mathMetaphor else null
                correctMathMetaphor(lastMathMetaphor, nextMathMetaphor)
            } else thisMathMetaphor

            val correctNextMathMeta = if (nextMathMetaphor == MathMetaphor.OP) {
                val nextNextIndex = if (nextIndex >= 0) {
                    placeables[nextIndex].nextReferenceIndex()
                } else -1
                val nextNextMathMetaphor = if (nextNextIndex >= 0)
                    measurables[nextNextIndex].mathMetaphor else null
                correctMathMetaphor(thisMathMetaphor, nextNextMathMetaphor)
            } else nextMathMetaphor

            return maxOf(
                mathMetaphorSpacing[correctThisMathMeta]!!,
                mathMetaphorSpacing[correctNextMathMeta]!!
            )
        }

        val offsets = mutableListOf<IntOffset>()
        var x = 0

        placeables.forEachIndexed { index, placeable ->
            val measurable = measurables[index]

            val y = heightAboveWaistLine - waistLines[index]
            // The original alignmentLines information can be discarded
            ceilingLines[index] += y
            // No need to update waistLines to save a little time
            floorLines[index] += y
            basementLines[index] += y

            val lastIndex = placeable.lastReferenceIndex()
            val lastMathMetaphor = if (lastIndex < 0
            ) null else measurables[lastIndex].mathMetaphor
            val thisMathMetaphor = measurable.mathMetaphor
            val nextIndex = placeable.nextReferenceIndex()
            val nextMathMetaphor = if (nextIndex < 0
            ) null else measurables[nextIndex].mathMetaphor

            offsets.add(IntOffset(x, y))

            if (measurable.alignment in doubleScript) {
                if (index - 2 >= 0 &&
                    measurables[index - 2].mathMetaphor == MathMetaphor.COLLECTIVEOP
                ) {
                    val width = placeables.subList(index - 2, index + 1).maxOf { it.width }
                    for (i in index - 2..index) {
                        offsets[i] = offsets[i] + IntOffset((width - placeables[i].width) / 2, 0)
                    }
                    x += width + placeable.calcSpacing()
                } else {
                    // adjust for right aligned super- or sub-scripts
                    val width = placeables.subList(index - 1, index + 1).maxOf { it.width }
                    if (placeable.lastReferenceIndex() < 0 &&
                        placeable.nextReferenceIndex() >= 0
                    ) {
                        for (i in index - 1..index) {
                            offsets[i] = offsets[i] + IntOffset(width - placeables[i].width, 0)
                        }
                    }
                    x += width + placeable.calcSpacing()
                }
            } else if (measurable.alignment != AlignmentMetaphor.UNSPECIFIED) {
                if (index >= measurables.lastIndex ||
                    measurables[index + 1].alignment !in doubleScript
                ) {
                    if (index > 0 &&
                        measurables[index - 1].mathMetaphor == MathMetaphor.COLLECTIVEOP
                    ) {
                        val width = placeables.subList(index - 1, index + 1).maxOf { it.width }
                        for (i in index - 1..index) {
                            offsets[i] =
                                offsets[i] + IntOffset((width - placeables[i].width) / 2, 0)
                        }
                        x += width + placeable.calcSpacing()
                    } else {
                        x += placeable.width + placeable.calcSpacing()
                    }
                }
            } else if (thisMathMetaphor != MathMetaphor.COLLECTIVEOP ||
                index + 1 >= measurables.lastIndex ||
                measurables[index + 1].alignment == AlignmentMetaphor.UNSPECIFIED
            ) {
                x += placeable.width + if (index + 1 <= placeables.lastIndex &&
                    measurables[index + 1].alignment == AlignmentMetaphor.UNSPECIFIED
                ) placeable.calcSpacing() else 0
            } // All remaining cases have no need to change x
        }
        val boxWidth = x
        val boxHeight = heightAboveWaistLine + heightBelowWaistLine

        /*
         * Add parenthesis
         */
        if (turtlePath.isEmpty) with(turtlePath) {
            placeables.forEachIndexed { index, placeable ->
                val measurable = measurables[index]
                val parenthesis = measurable.parenthesis

                if (parenthesis != null) {
                    val x: Float
                    val dirConst: Int

                    var referenceIndex =
                        if (parenthesis.directionOfOpening == LayoutDirection.Ltr) {
                            x = (offsets[index].x + placeable.width.toFloat())
                            dirConst = -1
                            placeable.nextReferenceIndex()
                        } else if (parenthesis.directionOfOpening == LayoutDirection.Rtl) {
                            x = offsets[index].x.toFloat()
                            dirConst = 1
                            placeable.lastReferenceIndex()
                        } else {
                            x = offsets[index].x + placeable.width / 2f
                            dirConst = 0
                            index
                        }
                    if (referenceIndex < 0) referenceIndex = index

                    val pWidth = placeable.width.toFloat()
                    val pHeight = placeables[referenceIndex]
                        .measureAlignment(BasementLine)
                        .toFloat()

                    val y = offsets[referenceIndex].y.toFloat()

                    when (parenthesis.type) {
                        '(', ')' -> {
                            moveTo(x, y + pWidth / 2)
                            val rect = Rect(
                                x - pWidth,
                                y + pWidth / 2,
                                x + pWidth,
                                y + pHeight - pWidth / 2
                            )
                            arcTo(rect, -90f, dirConst * 180f, true)
                        }

                        '[', ']' -> {
                            moveTo(x, y + pWidth / 2)
                            relativeLineTo(pWidth * dirConst / 2, 0f)
                            relativeLineTo(0f, pHeight - pWidth)
                            relativeLineTo(-pWidth * dirConst / 2, 0f)
                        }

                        '{', '}' -> {
                            moveTo(x, y + pWidth / 2)
                            val rect = Rect(
                                x - pWidth / 2,
                                y + pWidth / 2,
                                x + pWidth / 2,
                                y + pWidth * 1.5f
                            )
                            arcTo(rect, -90f, dirConst * 90f, true)
                            relativeLineTo(0f, pHeight / 2 - pWidth * 1.5f)
                            arcTo(
                                rect.translate(pWidth * dirConst, pHeight / 2 - pWidth * 3 / 2),
                                (dirConst + 1) * 90f, -dirConst * 90f, true
                            )
                            arcTo(
                                rect.translate(pWidth * dirConst, pHeight / 2 - pWidth / 2),
                                -90f, -dirConst * 90f, true
                            )
                            relativeLineTo(0f, pHeight / 2 - pWidth * 1.5f)
                            arcTo(
                                rect.translate(0f, pHeight - pWidth * 2),
                                (dirConst - 1) * 90f, dirConst * 90f, true
                            )
                        }

                        '<', '>' -> {
                            moveTo(x, y + pWidth / 2)
                            relativeLineTo(pWidth * dirConst, pHeight / 2 - pWidth / 2)
                            relativeLineTo(-pWidth * dirConst, pHeight / 2 - pWidth / 2)
                        }

                        '|' -> {
                            moveTo(x, y + pWidth / 2)
                            relativeLineTo(0f, pHeight - pWidth)
                        }

                    }
                }
            }
        }

        /*
         * Calculate global alignment lines
         */
        val alignmentLinesMap = mutableMapOf<AlignmentLine, Int>(
            CeilingLine to ceilingLines.min(),
            WaistLine to heightAboveWaistLine,
            FloorLine to floorLines.max(),
            BasementLine to basementLines.max()
        )
        val hasDecorationLine = hasOverlines.reduce { a, b -> a || b }.and(OverlineConst)
            .plus(hasUnderlines.reduce { a, b -> a || b }.and(UnderlineConst))
        if (hasDecorationLine > 0) alignmentLinesMap[HasDecorationLine] = hasDecorationLine

        /*
         * Placement
         */
        layout(
            width = boxWidth,
            height = boxHeight,
            alignmentLines = alignmentLinesMap
        ) {
            placeables.forEachIndexed() { index, placeable ->
                placeable.place(offsets[index])
            }
        }
    }
}

private object LaTeX {
    private fun Path.moveTo(x: Int, y: Int): Unit {
        this.moveTo(x.toFloat(), y.toFloat())
    }

    private fun Path.lineTo(x: Int, y: Int): Unit {
        this.lineTo(x.toFloat(), y.toFloat())
    }

    private fun Path.relativeMoveTo(x: Int, y: Int): Unit {
        this.relativeMoveTo(x.toFloat(), y.toFloat())
    }

    private fun Path.relativeLineTo(x: Int, y: Int): Unit {
        this.relativeLineTo(x.toFloat(), y.toFloat())
    }

    val fontCorrectionMatrix = mapOf(
        "sum" to listOf(
            Pair(1, 14), Pair(13, 28), Pair(12, 14), Pair(13, 14),
            Pair(1, 1), Pair(0, 1), Pair(1, 4)
        ),
        "prod" to listOf(
            Pair(1, 14), Pair(13, 28), Pair(12, 14), Pair(13, 14),
            Pair(1, 1), Pair(0, 1), Pair(1, 4)
        ),
        "int" to listOf(
            Pair(6, 70), Pair(38, 70), Pair(1, 1), Pair(76, 70),
            Pair(14, 10), Pair(2, 10), Pair(2, 10)
        ),
        "iint" to listOf(
            Pair(6, 70), Pair(38, 70), Pair(1, 1), Pair(76, 70),
            Pair(1, 1), Pair(0, 1), Pair(2, 10)
        ),
        "iiint" to listOf(
            Pair(6, 70), Pair(38, 70), Pair(1, 1), Pair(76, 70),
            Pair(1, 1), Pair(0, 1), Pair(2, 10)
        ),
        "oint" to listOf(
            Pair(6, 70), Pair(38, 70), Pair(1, 1), Pair(76, 70),
            Pair(36, 40), Pair(-1, 16), Pair(2, 10)
        ),
        "oiint" to listOf(
            Pair(6, 70), Pair(38, 70), Pair(1, 1), Pair(76, 70),
            Pair(37, 40), Pair(-1, 20), Pair(2, 10)
        ),
        "oiiint" to listOf(
            Pair(6, 70), Pair(38, 70), Pair(1, 1), Pair(76, 70),
            Pair(38, 40), Pair(-1, 36), Pair(2, 10)
        ),
        "arrow" to listOf(
            Pair(2, 14), Pair(4, 14), Pair(6, 14), Pair(8, 14),
            Pair(1, 1), Pair(0, 1), Pair(36, 70)
        )
    )

    @Composable
    fun FontCorrection(
        modifier: Modifier = Modifier,
        fontSize: TextUnit,
        content: @Composable LaTeXScope.() -> Unit
    ) {
        val debug = false
        val turtlePath = Path()

        Layout(
            content = { LaTeXScope.content() },
            modifier = if (debug) {
                modifier.drawBehind {
                    drawPath(
                        path = turtlePath,
                        color = Color.Red,
                        style = Stroke(width = 1f)
                    )
                }
            } else {
                modifier
            }
        ) { measurables, constraints ->
            if (measurables.size != 1) {
                return@Layout layout(constraints.minWidth, constraints.minHeight) {}
            }

            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val measurable = measurables[0]
            val placeable = measurable.measure(looseConstraints)
            val refHeight = placeable[FirstBaseline]

            fun Int.correct(constPair: Pair<Int, Int>): Int {
                return (this * constPair.first + constPair.second / 2) / constPair.second
            }

            val correctionPair =
                fontCorrectionMatrix[measurable.fontCorrectionInfo]!!

            val alignmentLinesMap = mutableMapOf<AlignmentLine, Int>(
                CeilingLine to refHeight.correct(correctionPair[0]),
                WaistLine to refHeight.correct(correctionPair[1]),
                FloorLine to refHeight.correct(correctionPair[2]),
                BasementLine to refHeight.correct(correctionPair[3])
            )

            val correctWidth = placeable.width.correct(correctionPair[4])
            val correctHeight = alignmentLinesMap[BasementLine]!!

            if (debug && turtlePath.isEmpty) with(turtlePath) {
                moveTo(0, 0)
                relativeLineTo(correctWidth, 0)
                relativeLineTo(0, correctHeight)
                relativeLineTo(-correctWidth, 0)
                relativeLineTo(0, -correctHeight)
                moveTo(0, alignmentLinesMap[CeilingLine]!!)
                relativeLineTo(correctWidth, 0)
                moveTo(0, alignmentLinesMap[WaistLine]!!)
                relativeLineTo(correctWidth, 0)
                moveTo(0, alignmentLinesMap[FloorLine]!!)
                relativeLineTo(correctWidth, 0)
            }

            layout(
                width = correctWidth,
                height = alignmentLinesMap[BasementLine]!!,
                alignmentLines = alignmentLinesMap
            ) {
                placeable.place(
                    x = placeable.width.correct(correctionPair[5]),
                    y = -refHeight.correct((correctionPair[6]))
                )
            }
        }
    }

    @Composable
    fun CustomFont(
        modifier: Modifier = Modifier,
        fontSize: TextUnit,
        content: @Composable LaTeXScope.() -> Unit
    ) {
        val turtlePath = Path()

        Layout(
            content = {
                LaTeXScope.content()
                Text(
                    text = Command2Unicode[";"]!!,
                    fontSize = fontSize
                )
            },
            modifier = modifier
                .drawBehind {
                    drawPath(
                        path = turtlePath,
                        color = Color.Black,
                        style = Stroke(width = fontSize.times(0.09f).toPx())
                    )
                }
        ) { measurables, constraints ->
            if (measurables.size != 2) {
                return@Layout layout(constraints.minWidth, constraints.minHeight) {}
            }

            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val dummyPlaceable = measurables[1].measure(looseConstraints)

            val fontWidth = dummyPlaceable.width
            val fontHeight = dummyPlaceable.height

            if (turtlePath.isEmpty) with(turtlePath) {
                val midLinePos = fontHeight * 0.75f
                moveTo(0f, midLinePos)
                relativeLineTo(fontWidth, 0)
                if (measurables[0].customFontChar == '+') {
                    moveTo(fontWidth * 0.5f, midLinePos - fontWidth * 0.5f)
                    relativeLineTo(0, fontWidth)
                }
            }

            layout(
                width = dummyPlaceable.width,
                height = dummyPlaceable.height,
                alignmentLines = mapOf(
                    CeilingLine to (fontHeight * 6 + 7) / 14,
                    WaistLine to (fontHeight * 10 + 7) / 14,
                    FloorLine to (fontHeight * 12 + 7) / 14,
                    BasementLine to (fontHeight * 14 + 7) / 14
                )
            ) {
                dummyPlaceable.place(0, 0)
            }
        }
    }

    @Composable
    fun Frac(
        modifier: Modifier = Modifier,
        fontSize: TextUnit,
        content: @Composable LaTeXScope.() -> Unit
    ) {
        val turtlePath = Path()

        Layout(
            content = { LaTeXScope.content() },
            modifier = modifier
                .drawBehind {
                    drawPath(
                        path = turtlePath,
                        color = Color.Black,
                        style = Stroke(width = 2f)
                    )
                }
        ) { measurables, constraints ->
            if (measurables.size != 2) {
                return@Layout layout(constraints.minWidth, constraints.minHeight) {}
            }

            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val numerator = measurables[0].measure(looseConstraints)
            val denominator = measurables[1].measure(looseConstraints)
            val ceilingLine = numerator.measureAlignment(CeilingLine)
            val waistLine = numerator.measureAlignment(BasementLine)
            val hasOverline = numerator.measureHasDecorationLine(OverlineConst)
            val floorLine = waistLine + denominator.measureAlignment(FloorLine)
            val basementLine = waistLine + denominator.measureAlignment(BasementLine)
            val hasUnderline = denominator.measureHasDecorationLine(UnderlineConst)

            val fracBarMargin = fontSize.times(reduceFontFactor / 4).toPx()
            val fracBarLength =
                maxOf(numerator.width, denominator.width) + 2 * fracBarMargin.toInt()
            val boxWidth = (fracBarLength + 2 * fracBarMargin).toInt()
            val boxHeight = basementLine

            if (turtlePath.isEmpty) with(turtlePath) { // Skip if recomposition
                moveTo(fracBarMargin, waistLine.toFloat())
                relativeLineTo(fracBarLength, 0)
            }

            val alignmentLinesMap = mutableMapOf<AlignmentLine, Int>(
                CeilingLine to ceilingLine,
                WaistLine to waistLine,
                FloorLine to floorLine,
                BasementLine to basementLine
            )
            val hasDecorationLine = hasOverline.and(OverlineConst)
                .plus(hasUnderline.and(UnderlineConst))
            if (hasDecorationLine > 0) alignmentLinesMap[HasDecorationLine] = hasDecorationLine

            layout(
                width = boxWidth,
                height = boxHeight,
                alignmentLines = alignmentLinesMap
            ) {
                numerator.place(x = (boxWidth - numerator.width) / 2, y = 0)
                denominator.place(x = (boxWidth - denominator.width) / 2, y = waistLine)
            }
        }
    }

    @Composable
    fun Sqrt(
        modifier: Modifier = Modifier,
        fontSize: TextUnit,
        content: @Composable LaTeXScope.() -> Unit
    ) {
        val turtlePath = Path()

        Layout(
            content = { LaTeXScope.content() },
            modifier = modifier
                .drawBehind {
                    drawPath(
                        path = turtlePath,
                        color = Color.Black,
                        style = Stroke(width = 2f)
                    )
                }
        ) { measurables, constraints ->
            if (measurables.size !in 1..2) {
                return@Layout layout(constraints.minWidth, constraints.minHeight) {}
            }

            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables =
                measurables.map { measurable -> measurable.measure(looseConstraints) }

            val index: Placeable? = if (placeables.size == 2) placeables[0] else null
            val radicand = placeables.last()

            var (ceilingLine, waistLine, floorLine, basementLine, hasOverline, hasUnderline)
                    = radicand.measureAlignmentsOrDecorationLines()

            val radicalSignWidth = fontSize.times(reduceFontFactor).roundToPx()
            val radicalSignHeight: Int
            val radicandMargin = fontSize.times(reduceFontFactor / 8).roundToPx()

            var x0 = 0 // index place at initially
            var y0 = 0
            var x1 = radicalSignWidth + radicandMargin // radicant place at initially
            var y1 = 0

            val vertPadding = fontSize.div(7).roundToPx()
            if (hasOverline) {
                waistLine += vertPadding
                floorLine += vertPadding
                basementLine += vertPadding
                y1 = vertPadding
            } else {
                ceilingLine -= vertPadding
            }
            floorLine += vertPadding
            if (hasUnderline) basementLine += vertPadding

            radicalSignHeight = floorLine - ceilingLine

            var xp = radicalSignWidth.times(1f / 14)
            val yp: Float
            if (index == null) {
                yp = radicalSignHeight.times(0.625f) + ceilingLine
            } else {
                val dx = index.width - radicalSignWidth.times(10f / 14)
                if (dx > 0) {
                    x1 += dx.toInt()
                    xp += dx
                } else {
                    x0 = -dx.toInt()
                }
                val indexHeight = index.measureAlignment(BasementLine)
                val dy = indexHeight - radicalSignHeight / 2 - ceilingLine
                if (dy > 0) {
                    ceilingLine += dy
                    waistLine += dy
                    floorLine += dy
                    basementLine += dy
                    y1 += dy
                    yp = indexHeight + radicalSignHeight.times(0.125f)
                } else {
                    y0 = -dy
                    yp = radicalSignHeight.times(0.625f) + ceilingLine
                }
                ceilingLine = minOf(index.measureAlignment(CeilingLine), ceilingLine)
            }

            if (turtlePath.isEmpty) with(turtlePath) { // Skip if recomposition
                moveTo(xp, yp)
                relativeLineTo(
                    radicalSignWidth.times(1f / 13),
                    radicalSignHeight.times(-0.125f)
                )
                relativeLineTo(radicalSignWidth.times(4f / 13), radicalSignHeight.times(0.5f))
                relativeLineTo(radicalSignWidth.times(8f / 13), radicalSignHeight.times(-1f))
                relativeLineTo(radicand.width + radicandMargin, 0)
            }

            layout(
                width = x1 + radicand.width + radicandMargin,
                height = basementLine,
                alignmentLines = mapOf(
                    CeilingLine to ceilingLine,
                    WaistLine to waistLine,
                    FloorLine to floorLine,
                    BasementLine to basementLine,
                    HasDecorationLine to OverlineConst + UnderlineConst
                )
            ) {
                index?.place(x0, y0)
                radicand.place(x1, y1)
            }
        }
    }

    @Composable
    fun Decorate(
        modifier: Modifier = Modifier,
        fontSize: TextUnit,
        content: @Composable LaTeXScope.() -> Unit
    ) {
        val turtlePath = Path()

        Layout(
            content = { LaTeXScope.content() },
            modifier = modifier
                .drawBehind {
                    drawPath(
                        path = turtlePath,
                        color = Color.Black,
                        style = Stroke(width = 1.5f)
                    )
                }
        ) { measurables, constraints ->
            if (measurables.size != 1) {
                return@Layout layout(constraints.minWidth, constraints.minHeight) {}
            }

            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val measurable = measurables[0]
            val placeable = measurable.measure(looseConstraints)

            var (ceilingLine, waistLine, floorLine, basementLine, hasOverline, hasUnderline)
                    = placeable.measureAlignmentsOrDecorationLines()
            val vertPadding = fontSize.div(7).roundToPx()
            var yPlace = 0

            if (measurable.decoration == DecorationMetaphor.UNDERLINE) {
                floorLine += vertPadding
                if (hasUnderline) {
                    basementLine += vertPadding
                } else {
                    hasUnderline = true
                }
                if (turtlePath.isEmpty) with(turtlePath) { // Skip if recomposition
                    moveTo(0, floorLine)
                    relativeLineTo(placeable.width, 0)
                }
            } else { // OVERLINE, OVERLEFTARROW or OVERRIGHTARROW
                if (hasOverline) {
                    waistLine += vertPadding
                    floorLine += vertPadding
                    basementLine += vertPadding
                    yPlace = vertPadding
                } else {
                    ceilingLine -= vertPadding
                    hasOverline = true
                }
                if (turtlePath.isEmpty) with(turtlePath) { // Skip if recomposition
                    val arrowWidth = vertPadding / 2
                    when (measurable.decoration) {
                        DecorationMetaphor.OVERLEFTARROW -> {
                            moveTo(placeable.width, ceilingLine)
                            relativeLineTo(-placeable.width, 0)
                            relativeMoveTo(arrowWidth, -arrowWidth)
                            relativeLineTo(-arrowWidth, arrowWidth)
                            relativeLineTo(arrowWidth, arrowWidth)
                        }

                        DecorationMetaphor.OVERRIGHTARROW -> {
                            moveTo(0, ceilingLine)
                            relativeLineTo(placeable.width, 0)
                            relativeMoveTo(-arrowWidth, -arrowWidth)
                            relativeLineTo(arrowWidth, arrowWidth)
                            relativeLineTo(-arrowWidth, arrowWidth)
                        }

                        else -> {
                            moveTo(0, ceilingLine)
                            relativeLineTo(placeable.width, 0)
                        }
                    }
                }
            }

            val alignmentLinesMap = mutableMapOf<AlignmentLine, Int>(
                CeilingLine to ceilingLine,
                WaistLine to waistLine,
                FloorLine to floorLine,
                BasementLine to basementLine
            )
            val hasDecorationLine = hasOverline.and(OverlineConst)
                .plus(hasUnderline.and(UnderlineConst))
            if (hasDecorationLine > 0) alignmentLinesMap[HasDecorationLine] = hasDecorationLine

            layout(
                width = placeable.width,
                height = basementLine,
                alignmentLines = alignmentLinesMap
            ) {
                placeable.place(0, yPlace)
            }
        }
    }

    @Composable
    fun Array(
        modifier: Modifier = Modifier,
        fontSize: TextUnit,
        columnAlignments: String = "",
        content: @Composable LaTeXScope.() -> Unit
    ) {
        val turtlePath = Path()

        Layout(
            content = { LaTeXScope.content() },
            modifier = modifier
                .drawBehind {
                    drawPath(
                        path = turtlePath,
                        color = Color.Black,
                        style = Stroke(width = 1.5f)
                    )
                }
        ) { measurables, constraints ->
            if (measurables.isEmpty()) {
                return@Layout layout(constraints.minWidth, constraints.minHeight) {}
            }

            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables =
                measurables.map { measurable -> measurable.measure(looseConstraints) }

            /*
            * Collect row heights and column widths
            */
            val columnWidth = ArrayList<Int>()
            val rowHeight = ArrayList<Int>()
            var rowNum = 0
            var colNum = 0

            placeables.forEachIndexed() { index, placeable ->
                val measurable = measurables[index]

                if (measurable.arrayCellMetaphor == ArrayCellMetaphor.NEWROW) {
                    rowHeight.add(0)
                    rowNum = rowHeight.lastIndex
                    colNum = 0
                }
                rowHeight[rowNum] = maxOf(
                    rowHeight[rowNum],
                    with(placeable) { measureAlignment(FloorLine) - measureAlignment(CeilingLine) }
                )
                if (columnWidth.lastIndex < colNum) columnWidth.add(0)
                columnWidth[colNum] = maxOf(
                    columnWidth[colNum],
                    placeable.width
                )
                colNum++
            }

            /*
             * Calculate row and column top-left corners
             */
            val padding = fontSize.div(2).roundToPx()

            val xPos = IntArray(columnWidth.size + 1)
            xPos[0] = padding
            for (i in 1 until xPos.lastIndex) {
                xPos[i] = xPos[i - 1] + columnWidth[i - 1] + padding
            }
            xPos[columnWidth.size] = xPos[columnWidth.size - 1] + columnWidth.last() + padding

            val yPos = IntArray(rowHeight.size + 1)
            yPos[0] = padding
            for (j in 1 until yPos.lastIndex) {
                yPos[j] = yPos[j - 1] + rowHeight[j - 1] + padding
            }
            yPos[rowHeight.size] = yPos[rowHeight.size - 1] + rowHeight.last() + padding

            /*
             * Calculate column alignments and vertical lines
             */
            val colAlignment = ArrayList<Char>()
            val vertLine = ArrayList<Int>()
            columnAlignments.forEach { char ->
                if (char in "lcr") {
                    colAlignment.add(char)
                } else if (char == '|'
                    && (vertLine.isEmpty() || vertLine.last() != colAlignment.size)
                ) {
                    vertLine.add(colAlignment.size)
                }
            }

            var i = 0
            if (turtlePath.isEmpty) while (i < vertLine.size && vertLine[i] <= columnWidth.size) {
                colNum = vertLine[i]
                with(turtlePath) {
                    moveTo(
                        x = xPos[colNum] - padding / 2,
                        y = padding / 2
                    )
                    relativeLineTo(0, yPos.last() - padding)
                }
                i++
            }

            /*
             * Placement
             */
            layout(
                width = xPos.last(),
                height = yPos.last(),
                alignmentLines = mapOf(
                    CeilingLine to padding,
                    WaistLine to yPos.last() / 2,
                    FloorLine to yPos.last() - padding,
                    BasementLine to yPos.last(),
                    HasDecorationLine to OverlineConst + UnderlineConst
                )
            ) {
                rowNum = -1

                placeables.forEachIndexed { index, placeable ->
                    val measurable = measurables[index]

                    if (measurable.arrayCellMetaphor == ArrayCellMetaphor.NEWROW) {
                        rowNum++
                        colNum = 0
                    }

                    val thisColAlignment = if (colNum < colAlignment.size)
                        colAlignment[colNum] else 'c'
                    val xPosAdj = xPos[colNum] + when (thisColAlignment) {
                        'l' -> 0
                        'r' -> columnWidth[colNum] - placeable.width
                        else -> (columnWidth[colNum] - placeable.width) / 2
                    }
                    val yPosAdj = yPos[rowNum] + with(placeable) {
                        rowHeight[rowNum]
                            .minus(measureAlignment(FloorLine))
                            .minus(measureAlignment(CeilingLine))
                    } / 2

                    placeable.place(xPosAdj, yPosAdj)
                    colNum++
                }
            }
        }
    }
}

private enum class ParseLaTeXStatus { NOERROR, ERROR, EXIT, ENDOFCELL, ENDOFROW, ENDOFBLOCK }
private enum class ParseLaTeXTypeStyle { NORMAL, MATHRM }

private object ParseLaTeX {
    private val RegexEndAtBrace =
        Regex("[<>]?=|[_^+\\-\\*<>{}\\\\]|[A-Za-z\\s]+|[^_^+\\-\\*<=>{}\\\\A-Za-z]+")
    private val RegexEndAtBracket =
        Regex("[<>]?=|[_^+\\-\\*<>{\\]\\\\]|[A-Za-z\\s]+|[^_^+\\-\\*<=>{\\]\\\\A-Za-z]+")
    private val RegexParseArray =
        Regex("\\\\\\\\|[<>]?=|[_^+\\-\\*<>{}\\\\&]|[A-Za-z\\s]+|[^_^+\\-\\*<=>{}\\\\&A-Za-z]+")
    private val RegexGetCommand =
        Regex("(?:left|right)[()\\[\\]{}<>|]|[A-Za-z][a-z]*|.?")
    private val RegexGetTag = Regex("\\{(array)\\}|")
    private val RegexGetArrayColumnAlignments = Regex("\\{([^}]*)\\}|")

    private var ptr = 0
    private var laText = ""
    var status = ParseLaTeXStatus.NOERROR
        private set
    private var typeStyle = ParseLaTeXTypeStyle.NORMAL

    fun reset(text: String) {
        ptr = 0
        this.laText = text
    }

    @Composable
    fun Main(
        modifier: Modifier = Modifier,
        fontSize: TextUnit,
        regex: Regex = RegexEndAtBrace
    ) {
        var meta: String?
        var lastMeta: String? = null
        var metaNoSpace: String

        status = ParseLaTeXStatus.NOERROR

        LaTeX(
            fontSize = fontSize,
            modifier = modifier
        ) {
            while (status == ParseLaTeXStatus.NOERROR && ptr < laText.length) {
                meta = regex.matchAt(laText, ptr)!!.groupValues[0]
                ptr += meta!!.length

                when (meta) {
                    "_", "^" -> {
                        ParseLaTeX.Singleton(
                            fontSize = fontSize.times(reduceFontFactor),
                            // No chaining previous modifiers
                            modifier = Modifier.align(
                                if (meta == "_") {
                                    if (lastMeta == "^") AlignmentMetaphor.SUBSCRIPT_
                                    else AlignmentMetaphor.SUBSCRIPT
                                } else {
                                    if (lastMeta == "_") AlignmentMetaphor.SUPERSCRIPT_
                                    else AlignmentMetaphor.SUPERSCRIPT
                                }
                            )
                        )
                        if (status == ParseLaTeXStatus.EXIT) status =
                            ParseLaTeXStatus.NOERROR
                    }

                    "\\" -> {
                        ParseLaTeX.Command(fontSize = fontSize)
                        if (status == ParseLaTeXStatus.EXIT) status = ParseLaTeXStatus.NOERROR
                    }

                    "{" -> {
                        ParseLaTeX.Main(fontSize = fontSize)
                        if (laText.charAt(ptr++) != '}') {
                            status = ParseLaTeXStatus.ERROR
                        } else if (status == ParseLaTeXStatus.EXIT) {
                            status = ParseLaTeXStatus.NOERROR
                        }
                    }

                    "}" -> {
                        ptr--
                        status = ParseLaTeXStatus.EXIT
                    }

                    "&" -> {
                        status = ParseLaTeXStatus.ENDOFCELL
                    }

                    "\\\\" -> {
                        status = ParseLaTeXStatus.ENDOFROW
                    }

                    "+", "-" -> {
                        LaTeX.CustomFont(
                            modifier = Modifier.attachMathMetaphor(MathMetaphor.OP),
                            fontSize = fontSize
                        ) {
                            Text(
                                text = meta!!,
                                fontSize = fontSize,
                                modifier = Modifier.setCustomFontChar(meta!!.first())
                            )
                        }
                    }

                    "*" -> {
                        Text(
                            text = meta!!,
                            fontSize = fontSize,
                            // No chaining previous modifiers
                            modifier = Modifier.attachMathMetaphor(MathMetaphor.OP)
                        )
                    }

                    "<", "<=", "=", ">=", ">" -> {
                        Text(
                            text = Command2Unicode[meta]!!,
                            fontSize = fontSize,
                            // No chaining previous modifiers
                            modifier = Modifier.attachMathMetaphor(MathMetaphor.COMPARATOR)
                        )
                    }

                    else -> if (meta == "]" && regex == RegexEndAtBracket) {
                        ptr--
                        status = ParseLaTeXStatus.EXIT
                    } else {
                        metaNoSpace = meta!!.replace(" ", "")
                        if (metaNoSpace.isNotEmpty()) {
                            Text(
                                text = metaNoSpace,
                                fontSize = fontSize,
                                fontStyle = if (typeStyle == ParseLaTeXTypeStyle.NORMAL &&
                                    metaNoSpace.all { it.isLetter() }
                                ) FontStyle.Italic else FontStyle.Normal
                            )
                        }
                        meta = null
                    }
                }

                lastMeta =
                    if (lastMeta == "^" && meta == "_") "^_"
                    else if (lastMeta == "_" && meta == "^") "_^"
                    else meta
            }
            if (status == ParseLaTeXStatus.NOERROR) status = ParseLaTeXStatus.EXIT
        }
    }

    @Composable
    fun Singleton(
        fontSize: TextUnit,
        modifier: Modifier = Modifier
    ) {
        when (val char = laText.charAt(ptr++)) { // only the following 3 possibilities
            '\\' -> {
                ParseLaTeX.Command(fontSize = fontSize, modifier = modifier)
            }

            '{' -> {
                ParseLaTeX.Main(fontSize = fontSize, modifier = modifier)
                if (laText.charAt(ptr++) != '}') {
                    status = ParseLaTeXStatus.ERROR
                }
            }

            else -> {
                Text(
                    modifier = modifier,
                    text = "$char",
                    fontSize = fontSize,
                    fontStyle = if (typeStyle == ParseLaTeXTypeStyle.NORMAL &&
                        char!!.isLetter()
                    ) FontStyle.Italic else FontStyle.Normal
                )
                status = ParseLaTeXStatus.EXIT
            }
        }
    }

    @Composable
    fun Command(
        fontSize: TextUnit,
        modifier: Modifier = Modifier
    ) {
        val command: String = RegexGetCommand.matchAt(laText, ptr)!!.groupValues[0]

        ptr += command.length
        when (command) {
            "begin" -> {
                ParseLaTeX.Block(fontSize = fontSize, modifier = modifier)
            }

            "end" -> {
                status = ParseLaTeXStatus.ENDOFBLOCK
            }

            "mathrm" -> {
                val currentTypeStyle = typeStyle
                typeStyle = ParseLaTeXTypeStyle.MATHRM
                ParseLaTeX.Singleton(fontSize = fontSize, modifier = modifier)
                typeStyle = currentTypeStyle
            }

            "frac", "dfrac" -> {
                val fSize =
                    if (command == "frac") fontSize.times(reduceFontFactor) else fontSize
                LaTeX.Frac(fontSize = fSize, modifier = modifier) {
                    // No chaining previous modifiers
                    ParseLaTeX.Singleton(fontSize = fSize)
                    if (status != ParseLaTeXStatus.ERROR) ParseLaTeX.Singleton(fontSize = fSize)
                }
            }

            "sqrt" -> {
                LaTeX.Sqrt(
                    fontSize = fontSize,
                    modifier = modifier.attachMathMetaphor(MathMetaphor.FUNCTION)
                ) {
                    if (laText.charAt(ptr) == '[') {
                        ptr++
                        ParseLaTeX.Main(
                            fontSize = fontSize.times(reduceFontFactor * reduceFontFactor),
                            regex = RegexEndAtBracket
                        )
                        if (laText.charAt(ptr++) != ']') status = ParseLaTeXStatus.ERROR
                    }
                    if (status != ParseLaTeXStatus.ERROR) {
                        ParseLaTeX.Singleton(fontSize = fontSize)
                    }
                }
            }

            "left(", "right)", "left[", "right]", "left{", "right}", "left<", "right>", "left|", "right|" -> {
                Text(
                    text = " ",
                    fontSize = fontSize,
                    modifier = modifier.parenthesis(
                        directionOfOpening = when (command.dropLast(1)) {
                            "left" -> LayoutDirection.Ltr
                            "right" -> LayoutDirection.Rtl
                            else -> null
                        },
                        parenthesis = command.last()
                    )
                )
            }

            "leftarrow", "uparrow", "rightarrow", "downarrow",
            "leftrightarrow", "updownarrow", "nwarrow", "nearrow",
            "searrow", "swarrow", "Leftarrow", "Uparrow",
            "Rightarrow", "Downarrow", "Leftrightarrow", "Updownarrow" -> {
                LaTeX.FontCorrection(
                    modifier = modifier.attachMathMetaphor(MathMetaphor.FUNCTION),
                    fontSize = fontSize
                ) {
                    Text(
                        text = Command2Unicode[command]!!,
                        fontSize = fontSize,
                        modifier = Modifier.setFontCorrectionInfo("arrow")
                    )
                }
            }

            "lim", "Lim" -> {
                Text(
                    text = command.lowercase(),
                    fontSize = fontSize,
                    modifier = if (command.first().isUpperCase()) {
                        modifier.attachMathMetaphor(MathMetaphor.COLLECTIVEOP)
                    } else {
                        modifier
                    }
                )
            }

            "int", "Int", "iint", "Iint", "iiint", "Iiint",
            "oint", "Oint", "oiint", "Oiint", "oiiint", "Oiiint",
            "sum", "Sum", "prod", "Prod" -> {
                LaTeX.FontCorrection(
                    modifier = if (command.first().isUpperCase()) {
                        modifier.attachMathMetaphor(MathMetaphor.COLLECTIVEOP)
                    } else {
                        modifier
                    },
                    fontSize = fontSize
                ) {
                    Text(
                        text = Command2Unicode[command.lowercase()]!!,
                        fontSize = fontSize.times(
                            if (command.takeLast(3).lowercase() == "int") 2.5f else 2f
                        ),
                        modifier = Modifier.setFontCorrectionInfo(command.lowercase())
                    )
                }
            }

            "overline", "underline", "overleftarrow", "overrightarrow" -> {
                LaTeX.Decorate(
                    modifier = modifier,
                    fontSize = fontSize
                ) {
                    ParseLaTeX.Singleton(
                        fontSize = fontSize,
                        // No chaining previous modifiers
                        modifier = Modifier.decorate(DecorationMetaphor.fromString(command))
                    )
                }
            }

            else -> {
                if (Command2Unicode.keys.contains(command)) {
                    val text = Command2Unicode[command]
                    Text(
                        text = text!!,
                        fontSize = fontSize,
                        modifier = if (command == "times" || command == "div") {
                            modifier.attachMathMetaphor(MathMetaphor.OP)
                        } else if (text.length > 1) {
                            modifier.attachMathMetaphor(MathMetaphor.FUNCTION)
                        } else modifier
                    )
                } else Text(
                    text = "\\$command",
                    fontSize = fontSize,
                    color = Color.Red,
                    modifier = modifier
                )
                status = ParseLaTeXStatus.EXIT
            }
        }
    }

    @Composable
    fun Block(
        fontSize: TextUnit,
        modifier: Modifier = Modifier
    ) {
        var tags = RegexGetTag.matchAt(laText, ptr)!!.groupValues
        ptr += tags[0].length
        when (tags[1]) {
            "array" -> {
                val columnAlignments = RegexGetArrayColumnAlignments
                    .matchAt(laText, ptr)!!.groupValues
                ptr += columnAlignments[0].length
                LaTeX.Array(
                    modifier = modifier,
                    fontSize = fontSize,
                    columnAlignments = columnAlignments[1]
                ) {
                    status = ParseLaTeXStatus.ENDOFROW
                    while (status != ParseLaTeXStatus.ENDOFBLOCK
                        && status != ParseLaTeXStatus.ERROR
                        && ptr < laText.length
                    ) {
                        ParseLaTeX.Main(
                            modifier = if (status == ParseLaTeXStatus.ENDOFROW) {
                                Modifier.attachArrayCellMetaphor(metaphor = ArrayCellMetaphor.NEWROW)
                            } else {
                                Modifier
                            },
                            fontSize = fontSize,
                            regex = RegexParseArray
                        )
                    }
                    if (status != ParseLaTeXStatus.ERROR) {
                        tags = RegexGetTag.matchAt(laText, ptr)!!.groupValues
                        ptr += tags[0].length
                        status = if (tags[1] == "array")
                            ParseLaTeXStatus.EXIT else ParseLaTeXStatus.ERROR
                    }
                }
            }

            else -> {
                status = ParseLaTeXStatus.ERROR
            }
        }
    }
}

private enum class AlignmentMetaphor { SUPERSCRIPT, SUPERSCRIPT_, SUBSCRIPT, SUBSCRIPT_, UNSPECIFIED }

private val doubleScript = listOf(AlignmentMetaphor.SUPERSCRIPT_, AlignmentMetaphor.SUBSCRIPT_)

private enum class DecorationMetaphor {
    OVERLINE, UNDERLINE, OVERLEFTARROW, OVERRIGHTARROW, UNSPECIFIED;

    companion object {
        fun fromString(string: String): DecorationMetaphor =
            DecorationMetaphor.values().reduce { s, t ->
                if (s.toString() == string.uppercase()) s else t
            }
    }
}

private enum class ArrayCellMetaphor { NEWROW, UNSPECIFIED }

// Operators are default to unary op, then interpreted as binary op in spacing when necessary
private enum class MathMetaphor { OP, UNARYOP, COMPARATOR, FUNCTION, COLLECTIVEOP, UNSPECIFIED }

private interface LaTeXScope {
    @Stable
    fun Modifier.align(alignment: AlignmentMetaphor) = this.then(
        LaTeXAlignment(alignment = alignment)
    )

    @Stable
    fun Modifier.parenthesis(
        directionOfOpening: LayoutDirection?,
        parenthesis: Char
    ) = this.then(
        LaTeXParenthesis(
            directionOfOpening = directionOfOpening,
            type = parenthesis
        )
    )

    @Stable
    fun Modifier.decorate(decoration: DecorationMetaphor) = this.then(
        LaTeXDecoration(decoration = decoration)
    )

    @Stable
    fun Modifier.attachArrayCellMetaphor(metaphor: ArrayCellMetaphor) = this.then(
        LaTeXArrayCell(metaphor = metaphor)
    )

    @Stable
    fun Modifier.attachMathMetaphor(metaphor: MathMetaphor) = this.then(
        LaTeXMathMetaphor(metaphor = metaphor)
    )

    @Stable
    fun Modifier.setFontCorrectionInfo(info: String) = this.then(
        LaTeXFontCorrectionInfo(info = info)
    )

    @Stable
    fun Modifier.setCustomFontChar(info: Char) = this.then(
        LaTeXCustomFontChar(char = info)
    )

    companion object : LaTeXScope
}

private class LaTeXAlignment(
    val alignment: AlignmentMetaphor
) : ParentDataModifier {

    override fun Density.modifyParentData(parentData: Any?) = this@LaTeXAlignment

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? LaTeXAlignment ?: return false

        return (alignment == otherModifier.alignment)
    }

    override fun toString(): String =
        "LaTeXAlignment(alignment=$alignment)"

    override fun hashCode(): Int {
        return alignment.hashCode()
    }
}

private val Measurable.alignment: AlignmentMetaphor
    get() {
        val childData = parentData as? LaTeXAlignment
        return childData?.alignment ?: AlignmentMetaphor.UNSPECIFIED
    }

private class LaTeXParenthesis(
    val directionOfOpening: LayoutDirection?,
    val type: Char?
) : ParentDataModifier {

    override fun Density.modifyParentData(parentData: Any?) = this@LaTeXParenthesis

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LaTeXParenthesis

        if (directionOfOpening != other.directionOfOpening) return false
        if (type != other.type) return false

        return true
    }

    override fun toString(): String =
        "LaTeXParenthesis(directionOfOpening=$directionOfOpening, type=$type)"

    override fun hashCode(): Int {
        var result = directionOfOpening?.hashCode() ?: 0
        result = 31 * result + (type?.hashCode() ?: 0)
        return result
    }
}

private val Measurable.parenthesis: LaTeXParenthesis?
    get() {
        val childData = parentData as? LaTeXParenthesis
        return childData
    }

private class LaTeXDecoration(
    val decoration: DecorationMetaphor
) : ParentDataModifier {

    override fun Density.modifyParentData(parentData: Any?) = this@LaTeXDecoration

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? LaTeXDecoration ?: return false

        return (decoration == otherModifier.decoration)
    }

    override fun toString(): String =
        "LaTeXDecoration(decoration=$decoration)"

    override fun hashCode(): Int {
        return decoration.hashCode()
    }
}

private val Measurable.decoration: DecorationMetaphor
    get() {
        val childData = parentData as? LaTeXDecoration
        return childData?.decoration ?: DecorationMetaphor.UNSPECIFIED
    }

private class LaTeXArrayCell(
    val metaphor: ArrayCellMetaphor
) : ParentDataModifier {

    override fun Density.modifyParentData(parentData: Any?) = this@LaTeXArrayCell

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? LaTeXArrayCell ?: return false

        return (metaphor == otherModifier.metaphor)
    }

    override fun toString(): String =
        "LaTeXArrayCell(metaphor=$metaphor)"

    override fun hashCode(): Int {
        return metaphor.hashCode()
    }
}

private val Measurable.arrayCellMetaphor: ArrayCellMetaphor
    get() {
        val childData = parentData as? LaTeXArrayCell
        return childData?.metaphor ?: ArrayCellMetaphor.UNSPECIFIED
    }

private class LaTeXMathMetaphor(
    val metaphor: MathMetaphor
) : ParentDataModifier {

    override fun Density.modifyParentData(parentData: Any?) = this@LaTeXMathMetaphor

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? LaTeXMathMetaphor ?: return false

        return (metaphor == otherModifier.metaphor)
    }

    override fun toString(): String =
        "LaTeXMathMetaphor(metaphor=$metaphor)"

    override fun hashCode(): Int {
        return metaphor.hashCode()
    }
}

private val Measurable.mathMetaphor: MathMetaphor
    get() {
        val childData = parentData as? LaTeXMathMetaphor
        return childData?.metaphor ?: MathMetaphor.UNSPECIFIED
    }

private class LaTeXFontCorrectionInfo(
    val info: String
) : ParentDataModifier {

    override fun Density.modifyParentData(parentData: Any?) = this@LaTeXFontCorrectionInfo

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? LaTeXFontCorrectionInfo ?: return false

        return (info == otherModifier.info)
    }

    override fun toString(): String =
        "LaTeXFontCorrectionInfo(info=$info)"

    override fun hashCode(): Int {
        return info.hashCode()
    }
}

private val Measurable.fontCorrectionInfo: String
    get() {
        val childData = parentData as? LaTeXFontCorrectionInfo
        return childData?.info ?: ""
    }

private class LaTeXCustomFontChar(
    val char: Char
) : ParentDataModifier {

    override fun Density.modifyParentData(parentData: Any?) = this@LaTeXCustomFontChar

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? LaTeXCustomFontChar ?: return false

        return (char == otherModifier.char)
    }

    override fun toString(): String =
        "LaTeXCustomFontChar(char=$char)"

    override fun hashCode(): Int {
        return char.hashCode()
    }
}

private val Measurable.customFontChar: Char
    get() {
        val childData = parentData as? LaTeXCustomFontChar
        return childData?.char ?: ' '
    }
