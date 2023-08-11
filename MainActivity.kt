package com.example.latex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.latex.ui.theme.LaTeXTheme
import com.example.latex.ui.theme.Typography

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaTeXTheme {
                // A surface container using the 'background' color from the theme
                Row() {
                    Greeting()
                }
            }
        }
    }
}

@Composable
fun Greeting() {
    LaTeX(
        laTeX = "\\left{" + "\\begin{array}{rc||l|}" +
                "+11 &  & 8{+}8 \\\\ & \\sin4\\degree & 8+8" +
                "\\\\ \\sqrt[3]\\frac12" +
                "\\end{array}\\right}", fontSize = Typography.bodyLarge.fontSize
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LaTeXTheme {
        Greeting()
    }
}