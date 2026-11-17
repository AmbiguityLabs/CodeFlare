package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.remote.WorkerLanguage
import com.example.editor.CodeEditHelper
import com.example.editor.CodeSyntaxVisualTransformation
import com.example.ui.theme.DarkIdePalette
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeEditHelperTest {

  @Test
  fun computesLineStartsAndCursorLineAndColWithoutAllocatingLines() {
    val text = "first\nsecond\n  third"
    assertEquals(3, CodeEditHelper.computeLineCount(text))
    assertArrayEquals(intArrayOf(0, 6, 13), CodeEditHelper.computeLineStarts(text))

    assertEquals(1 to 1, CodeEditHelper.computeCursorLineAndCol(text, 0))
    assertEquals(2 to 1, CodeEditHelper.computeCursorLineAndCol(text, 6))
    assertEquals(3 to 5, CodeEditHelper.computeCursorLineAndCol(text, 17))
  }

  @Test
  fun enterPreservesIndentAndSplitsEmptyBraces() {
    val before = "function test() {}"
    val braceCursor = before.indexOf('{') + 1
    val prev = TextFieldValue(before, TextRange(braceCursor))
    val afterNewline = TextFieldValue("function test() {\n}", TextRange(braceCursor + 1))

    val edited =
      CodeEditHelper.applySmartCodeEdit(
        prev = prev,
        next = afterNewline,
        language = WorkerLanguage.JAVASCRIPT,
        tabSize = 2,
        autoCloseBrackets = true,
      )

    assertEquals("function test() {\n  \n}", edited.text)
    assertEquals(TextRange(braceCursor + 3), edited.selection)
  }

  @Test
  fun enterPreservesExistingIndentationAndIndentsAfterOpenBrace() {
    val prev = TextFieldValue("  if (ok) {\n    doWork()", TextRange(24))
    val next = TextFieldValue("  if (ok) {\n    doWork()\n", TextRange(25))

    val edited =
      CodeEditHelper.applySmartCodeEdit(
        prev = prev,
        next = next,
        language = WorkerLanguage.JAVASCRIPT,
        tabSize = 2,
        autoCloseBrackets = true,
      )

    assertEquals("  if (ok) {\n    doWork()\n    ", edited.text)
    assertEquals(TextRange(29), edited.selection)
  }

  @Test
  fun autoClosesBracketsSkipsClosingBracketAndDeletesEmptyPair() {
    val prevOpen = TextFieldValue("const fn = ", TextRange(11))
    val typedOpen = TextFieldValue("const fn = (", TextRange(12))
    val closed =
      CodeEditHelper.applySmartCodeEdit(
        prev = prevOpen,
        next = typedOpen,
        language = WorkerLanguage.JAVASCRIPT,
        tabSize = 2,
        autoCloseBrackets = true,
      )
    assertEquals("const fn = ()", closed.text)
    assertEquals(TextRange(12), closed.selection)

    val typedClose = TextFieldValue("const fn = ())", TextRange(13))
    val skipped =
      CodeEditHelper.applySmartCodeEdit(
        prev = closed,
        next = typedClose,
        language = WorkerLanguage.JAVASCRIPT,
        tabSize = 2,
        autoCloseBrackets = true,
      )
    assertEquals("const fn = ()", skipped.text)
    assertEquals(TextRange(13), skipped.selection)

    val backspaced = TextFieldValue("const fn = )", TextRange(11))
    val deletedPair =
      CodeEditHelper.applySmartCodeEdit(
        prev = closed,
        next = backspaced,
        language = WorkerLanguage.JAVASCRIPT,
        tabSize = 2,
        autoCloseBrackets = true,
      )
    assertEquals("const fn = ", deletedPair.text)
    assertEquals(TextRange(11), deletedPair.selection)
  }

  @Test
  fun skipsSmartEditsDuringImeComposition() {
    val prev = TextFieldValue("a", TextRange(1))
    val composing = TextFieldValue("a(", TextRange(2), composition = TextRange(0, 2))
    val result =
      CodeEditHelper.applySmartCodeEdit(
        prev = prev,
        next = composing,
        language = WorkerLanguage.JAVASCRIPT,
        tabSize = 2,
        autoCloseBrackets = true,
      )
    assertEquals(composing, result)
  }

  @Test
  fun visualTransformationEqualityIsIndependentOfCursorAndDiagnostics() {
    val vt1 = CodeSyntaxVisualTransformation(WorkerLanguage.JAVASCRIPT, DarkIdePalette)
    val vt2 = CodeSyntaxVisualTransformation(WorkerLanguage.JAVASCRIPT, DarkIdePalette)
    assertEquals(vt1, vt2)
    assertEquals(vt1.hashCode(), vt2.hashCode())
  }
}
