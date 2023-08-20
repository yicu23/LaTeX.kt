package com.example.fxcalculator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.example.fxcalculator.LaTeX
import com.example.fxcalculator.ui.theme.FxCalculatorTheme
import com.example.fxcalculator.ui.theme.Typography

@Composable
fun FullScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        DisplayBar()
    }
}

@Composable
fun DisplayBar() {
    LaTeX(
        laTeX = "\\left{" + "\\begin{array}{rc||l|}" +
                "+1 &  & 8{+}8 \\\\ 1+-1 & 2\\sin4\\degree & 8+8" +
                "\\\\ \\sqrt[3]\\frac12" +
                "\\end{array}\\right}", fontSize = Typography.bodyLarge.fontSize
    )
    LaTeX(
        laTeX = "\\int_1^\\infty\\dfrac12\\mathrm{d}x",
        fontSize = Typography.bodyLarge.fontSize
    )
    LaTeX(
        laTeX = "\\Prod_{i=1}^{24}\\dfrac{i}2",
        fontSize = Typography.bodyLarge.fontSize
    )
    LaTeX(
        laTeX = "\\{f(x):A\\Rightarrow +B>=+\\ln(\\log{c})",
        fontSize = Typography.bodyLarge.fontSize
    )
    LaTeX(
        laTeX = "\\Lim_{x\\rightarrow\\infty}\\frac{x^2-\\sqrt[3]{x-1}}{x^2+\\sqrt[3]{x-1}}",
        fontSize = Typography.bodyLarge.fontSize
    )
    LaTeX(
        laTeX = "\\int\\iint\\iiint\\oint\\oiint\\oiiint",
        fontSize = Typography.bodyLarge.fontSize
    )
    LaTeX(
        laTeX = "\\Iint_{S}\\nabla\\times F\\cdot\\mathrm{d}S",
        fontSize = Typography.bodyLarge.fontSize
    )
    LaTeX(
        laTeX = "\\ldots\\cdots\\vdots\\ddots",
        fontSize = Typography.bodyLarge.fontSize
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FxCalculatorTheme {
        FullScreen()
    }
}
