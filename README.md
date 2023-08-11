# LaTeX.kt
Creates a Composable from text written in LaTeX syntax.

The two files LaTeX.kt and strings.xml are required.
Copy them to your project java/.. directory and res/values directory.
Correct the imports if necessary.

The file MainActivity.kt is for demonstration. You can run this to preview the result.

The LaTeX composable is not cached. Put the composable inside a Row, Column, etc. so that it can be cached.

Note that this composable does not support all LaTeX extensions, but most mathematical expressions are supported.
e.g. \frac, \sqrt, \overline, \underline, ^, _, \sin, \ln, \degree, \begin{array}{clr|clr}..\end{array}, \left{,

Please report for any bugs and submit your requests. Thanks for your support.
