package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.CloudflareClientFactory
import com.example.data.repository.CloudflareRepository
import com.example.data.security.AuthPreferencesStore
import com.example.terminal.LinuxTerminalEngine
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LinuxTerminalEngineTest {

  private fun createEngine(): Pair<LinuxTerminalEngine, File> {
    val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    val prefs = AuthPreferencesStore(ctx)
    val factory = CloudflareClientFactory(prefs)
    val repo = CloudflareRepository(prefs, factory)
    val tempRoot = createTempDirectory("edge_term_test").toFile()
    return LinuxTerminalEngine(ctx, repo, tempRoot) to tempRoot
  }

  private suspend fun runCmd(engine: LinuxTerminalEngine, cmd: String, sessionId: String? = null) =
    engine.execute(
      rawInput = cmd,
      workspace = null,
      account = null,
      history = emptyList(),
      onWranglerDeploy = { "ok" },
      onWranglerTailStart = { "ok" },
      onWranglerTailStop = { "ok" },
      onWranglerRollback = { "ok" },
      sessionId = sessionId,
    )

  @Test
  fun supportsCommandChainingPersistentCdExportAndAliases() = runBlocking {
    val (engine, root) = createEngine()
    val res1 = runCmd(engine, "mkdir -p demo/sub && cd demo/sub && pwd")
    assertFalse(res1.isError)
    assertTrue(res1.output.endsWith("/demo/sub"))

    val res2 = runCmd(engine, "pwd")
    assertEquals(File(root, "demo/sub").canonicalPath, res2.output.trim())

    val res3 = runCmd(engine, "false || echo fallback_ran")
    assertEquals("fallback_ran", res3.output.trim())

    runCmd(engine, "export MY_KEY=hello_linux")
    val res4 = runCmd(engine, "echo \$MY_KEY")
    assertEquals("hello_linux", res4.output.trim())

    runCmd(engine, "alias greet='echo hi'")
    val res5 = runCmd(engine, "greet terminal")
    assertEquals("hi terminal", res5.output.trim())
  }

  @Test
  fun supportsZipUnzipTreeHashBase64JqAndCalc() = runBlocking {
    val (engine, root) = createEngine()
    File(root, "src").mkdirs()
    File(root, "src/index.js").writeText("console.log('edge');")

    val treeRes = runCmd(engine, "tree -L 2")
    assertFalse(treeRes.isError)
    assertTrue(treeRes.output.contains("index.js"))

    val zipRes = runCmd(engine, "zip -r bundle.zip src")
    assertFalse(zipRes.isError)
    assertTrue(File(root, "bundle.zip").exists())

    val listRes = runCmd(engine, "unzip -l bundle.zip")
    assertTrue(listRes.output.contains("src/index.js"))

    val unzipRes = runCmd(engine, "unzip bundle.zip -d extracted")
    assertFalse(unzipRes.isError)
    assertTrue(File(root, "extracted/src/index.js").exists())

    val b64Res = runCmd(engine, "echo -n edgecode | base64 | base64 -d")
    assertEquals("edgecode", b64Res.output.trim())

    val shaRes = runCmd(engine, "sha256sum src/index.js")
    assertFalse(shaRes.isError)
    assertTrue(shaRes.output.trim().length > 64)

    val jqRes = runCmd(engine, "echo '{\"name\":\"worker-one\"}' | jq -r .name")
    assertEquals("worker-one", jqRes.output.trim())

    val calcRes = runCmd(engine, "calc (2+3)*4^2")
    assertEquals("80", calcRes.output.trim())
  }

  @Test
  fun supportsMultiSessionIsolationSysinfoPkgAndIdeBridge() = runBlocking {
    val (engine, root) = createEngine()
    File(root, "projA").mkdirs()
    File(root, "projB").mkdirs()

    runCmd(engine, "cd projA")
    val s2 = engine.createSession(null)
    runCmd(engine, "cd projB", sessionId = s2.id)

    assertEquals(File(root, "projA").canonicalPath, engine.currentDirPath("sh-1"))
    assertEquals(File(root, "projB").canonicalPath, engine.currentDirPath(s2.id))

    val sysRes = runCmd(engine, "sysinfo")
    assertFalse(sysRes.isError)
    assertTrue(sysRes.output.contains("edgecode-linux"))

    val pkgRes = runCmd(engine, "pkg")
    assertFalse(pkgRes.isError)
    assertTrue(pkgRes.output.contains("zip"))

    val nanoRes = runCmd(engine, "nano notes.txt")
    assertNotNull(nanoRes.editFileRequest)

    val ideRes = runCmd(engine, "ide projA")
    assertEquals("projA", ideRes.openProjectRequest)

    engine.closeSession(s2.id)
    assertEquals(1, engine.listSessionTabs(null).size)
  }

  @Test
  fun supportsRedirectionAndInputFromFile() = runBlocking {
    val (engine, _) = createEngine()
    val writeRes = runCmd(engine, "echo hello_world > note.txt")
    assertFalse(writeRes.isError)

    val readRes = runCmd(engine, "base64 < note.txt | base64 -d")
    assertFalse(readRes.isError)
    assertEquals("hello_world", readRes.output.trim())
  }
}
