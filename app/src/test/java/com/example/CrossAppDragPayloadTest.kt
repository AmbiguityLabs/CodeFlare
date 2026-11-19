package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.view.DragAndDropPermissions
import android.view.DragEvent
import androidx.test.core.app.ApplicationProvider
import com.example.editor.drag.DragTransferMode
import com.example.editor.drag.EdgeDragPayload
import com.example.editor.drag.EdgeTextDragCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrossAppDragPayloadTest {

  private fun obtainDropEvent(clipData: ClipData?, localState: Any? = null): DragEvent {
    val method =
      DragEvent::class.java.declaredMethods.first { m ->
        m.name == "obtain" &&
          m.parameterTypes.any { it == ClipData::class.java } &&
          m.parameterTypes.any { it == Any::class.java }
      }
    method.isAccessible = true
    var intIdx = 0
    var floatIdx = 0
    val args =
      method.parameterTypes.map { param ->
        when (param) {
          Int::class.javaPrimitiveType -> if (intIdx++ == 0) DragEvent.ACTION_DROP else 0
          Float::class.javaPrimitiveType ->
            when (floatIdx++) {
              0 -> 100f
              1 -> 120f
              else -> 0f
            }
          Boolean::class.javaPrimitiveType -> false
          Any::class.java -> localState
          ClipDescription::class.java -> clipData?.description
          ClipData::class.java -> clipData
          else -> null
        }
      }.toTypedArray()
    return method.invoke(null, *args) as DragEvent
  }

  @Test
  fun acceptsPlainHtmlAndTextSubtypesWhileRejectingBinaryMimes() {
    assertTrue(EdgeTextDragCoordinator.hasSupportedTextMime(ClipDescription("t", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN))))
    assertTrue(EdgeTextDragCoordinator.hasSupportedTextMime(ClipDescription("h", arrayOf(ClipDescription.MIMETYPE_TEXT_HTML))))
    assertTrue(EdgeTextDragCoordinator.hasSupportedTextMime(ClipDescription("js", arrayOf("text/javascript"))))

    assertFalse(EdgeTextDragCoordinator.hasSupportedTextMime(ClipDescription("img", arrayOf("image/png"))))
    assertFalse(EdgeTextDragCoordinator.hasSupportedTextMime(ClipDescription("pdf", arrayOf("application/pdf"))))
    assertFalse(EdgeTextDragCoordinator.hasSupportedTextMime(null))
  }

  @Test
  fun extractsPlainTextAndMultiItemClipDataCleanly() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val clip = ClipData.newPlainText("Snippet", "const port = 8787;")
    clip.addItem(ClipData.Item(""))
    clip.addItem(ClipData.Item("export default app;"))

    val extracted = EdgeTextDragCoordinator.extractTextFromClipData(clip, ctx)
    assertEquals("const port = 8787;\nexport default app;", extracted)
  }

  @Test
  fun stripsHtmlTagsAndDecodesEntitiesWhenOnlyHtmlPayloadPresent() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val desc = ClipDescription("Browser HTML", arrayOf(ClipDescription.MIMETYPE_TEXT_HTML))
    val item = ClipData.Item("", "<p>fetch(&quot;https://edge.dev&quot;)<br/>&lt;ok&gt; &amp; ready&#39;s&nbsp;go</p>")
    val clip = ClipData(desc, item)

    val extracted = EdgeTextDragCoordinator.extractTextFromClipData(clip, ctx)
    assertEquals("fetch(\"https://edge.dev\")\n<ok> & ready's go", extracted)
  }

  @Test
  fun nullOrEmptyClipDataReturnsNullSafely() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    assertNull(EdgeTextDragCoordinator.extractTextFromClipData(null, ctx))
    val emptyClip = ClipData.newPlainText("Empty", "")
    assertNull(EdgeTextDragCoordinator.extractTextFromClipData(emptyClip, ctx))
  }

  @Test
  fun findActivityUnwrapsNestedContextWrappers() {
    val controller = Robolectric.buildActivity(Activity::class.java).create()
    val activity = controller.get()
    val wrapped = ContextWrapper(ContextWrapper(activity))
    assertSame(activity, EdgeTextDragCoordinator.findActivity(wrapped))
    assertNull(EdgeTextDragCoordinator.findActivity(ApplicationProvider.getApplicationContext()))
  }

  @Test
  fun extractDroppedTextWithPermissionsPrefersInAppPayloadAndHandlesExternalClipSafely() {
    var permissionRequested = false
    val activity =
      object : Activity() {
        override fun requestDragAndDropPermissions(event: DragEvent?): DragAndDropPermissions? {
          permissionRequested = true
          return null
        }
      }
    val wrappedCtx = ContextWrapper(activity)

    // 1. In-app payload short-circuits without requesting cross-app permissions
    val inAppPayload =
      EdgeDragPayload(
        dragId = 77L,
        sourceSurfaceId = "editor_main",
        sourceEditable = true,
        sourceRange = 0..5,
        text = "export",
        preferredMode = DragTransferMode.MOVE,
        scopeLabel = "Token",
      )
    val inAppClip = ClipData.newPlainText("Token", "export")
    val inAppEvent = obtainDropEvent(inAppClip, localState = inAppPayload)
    assertEquals("export", EdgeTextDragCoordinator.extractDroppedTextWithPermissions(inAppEvent, wrappedCtx))
    assertFalse("In-app drop should not request cross-app permissions", permissionRequested)

    // 2. External cross-app drop requests DragAndDropPermissions and extracts ClipData cleanly
    val externalClip = ClipData.newPlainText("Chrome", "https://developers.cloudflare.com/workers/")
    val externalEvent = obtainDropEvent(externalClip, localState = null)
    assertEquals(
      "https://developers.cloudflare.com/workers/",
      EdgeTextDragCoordinator.extractDroppedTextWithPermissions(externalEvent, wrappedCtx),
    )
    assertTrue("External drop must request DragAndDropPermissions on Activity", permissionRequested)
  }
}
