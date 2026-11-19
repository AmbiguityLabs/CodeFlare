package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.CloudflareClientFactory
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import com.example.data.repository.CloudflareRepository
import com.example.data.security.AuthPreferencesStore
import com.example.terminal.CodeRunnerEngine
import com.example.terminal.LinuxTerminalEngine
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CodeRunnerEngineTest {

  private fun createEngine(): Pair<LinuxTerminalEngine, File> {
    val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    val prefs = AuthPreferencesStore(ctx)
    val factory = CloudflareClientFactory(prefs)
    val repo = CloudflareRepository(prefs, factory)
    val tempRoot = createTempDirectory("edge_runner_test").toFile()
    return LinuxTerminalEngine(ctx, repo, tempRoot) to tempRoot
  }

  private suspend fun runCmd(
    engine: LinuxTerminalEngine,
    cmd: String,
    workspace: WorkerWorkspace? = null,
  ) =
    engine.execute(
      rawInput = cmd,
      workspace = workspace,
      account = null,
      history = emptyList(),
      onWranglerDeploy = { "ok" },
      onWranglerTailStart = { "ok" },
      onWranglerTailStop = { "ok" },
      onWranglerRollback = { "ok" },
    )

  @Test
  fun executesNodeEvalPrintAndSyntaxCheck() = runBlocking {
    val (engine, _) = createEngine()

    val evalRes = runCmd(engine, "node -e \"const x = 6; console.log(x + 4)\"")
    assertFalse(evalRes.isError)
    assertEquals("10", evalRes.output.trim())

    val printRes = runCmd(engine, "node -p \"20 + 22\"")
    assertFalse(printRes.isError)
    assertEquals("42", printRes.output.trim())

    val syntaxOk = runCmd(engine, "node -e \"function ok() { return 1; }\" -c")
    assertFalse(syntaxOk.isError)
    assertTrue(syntaxOk.output.contains("Syntax OK"))

    val syntaxBad = runCmd(engine, "node -e \"function broken( { return 1; }\" -c")
    assertTrue(syntaxBad.isError)
    assertTrue(syntaxBad.output.contains("SyntaxError"))
  }

  @Test
  fun runsWorkspaceFileAndTypeScriptWithRelativeImports() = runBlocking {
    val (engine, _) = createEngine()
    val helperContent = "export const prefix: string = \"edge-\";"
    val indexContent = "import { prefix } from \"./helper\";\nconst suffix: number = 99;\nconsole.log(prefix + suffix);"
    val workspace =
      WorkerWorkspace(
        scriptName = "demo-runner",
        isEsModuleFormat = true,
        entryModulePath = "src/index.ts",
        compatibilityDate = "2025-01-01",
        files =
          listOf(
            WorkerFile(
              path = "src/helper.ts",
              content = helperContent,
              originalContent = helperContent,
              contentType = "application/typescript",
              isEntryPoint = false,
            ),
            WorkerFile(
              path = "src/index.ts",
              content = indexContent,
              originalContent = indexContent,
              contentType = "application/typescript",
              isEntryPoint = true,
            ),
          ),
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = false,
        accountSubdomain = "",
      )

    val res = runCmd(engine, "run", workspace = workspace)
    assertFalse(res.isError)
    assertTrue(res.output.contains("edge-99"))
  }

  @Test
  fun executesNpmScriptsAndShellScripts() = runBlocking {
    val (engine, root) = createEngine()
    File(root, "app.js").writeText("console.log('boot-ok');")
    File(root, "package.json").writeText(
      """{"name":"edge-cli","version":"1.2.3","scripts":{"test":"node app.js","greet":"echo npm-script-ran"}}""",
    )
    File(root, "hello.sh").writeText("#!/bin/sh\necho arg1=$1 count=$#\n")

    val npmTest = runCmd(engine, "npm test")
    assertFalse(npmTest.isError)
    assertEquals("boot-ok", npmTest.output.trim())

    val npmGreet = runCmd(engine, "npm run greet")
    assertFalse(npmGreet.isError)
    assertEquals("npm-script-ran", npmGreet.output.trim())

    val shRes = runCmd(engine, "sh hello.sh alpha beta")
    assertFalse(shRes.isError)
    assertEquals("arg1=alpha count=2", shRes.output.trim())

    val dotSlash = runCmd(engine, "./hello.sh one")
    assertFalse(dotSlash.isError)
    assertEquals("arg1=one count=1", dotSlash.output.trim())
  }

  @Test
  fun executesJsonValidationAndTypeStrippingUnitChecks() {
    val stripped =
      CodeRunnerEngine.stripTypeScriptSyntax(
        "import type { Foo } from './types';\ninterface Bar { x: number; }\nconst n: number = 7;\n",
      )
    assertFalse(stripped.contains("interface Bar"))
    assertFalse(stripped.contains("import type"))
    assertTrue(stripped.contains("const n = 7"))
  }
}
