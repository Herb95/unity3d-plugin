# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Jenkins plugin (`hpi` packaging) that adds an "Invoke Unity3d Editor" freestyle build step. Its main
value-add over just shelling out to the editor: Unity/Tuanjie write their console output to a separate
`Editor.log` file instead of stdout, so the plugin tails that file into the Jenkins build console in
real time, including when the build runs on a remote agent.

## Commands

`JAVA_HOME` must point at a JDK **17–21** and Maven itself must run on it. JDK 17+ is the
parent-POM minimum, but JDK 22+ breaks the build: the parent's `license-maven-plugin` (parses an
inline Groovy script) and `spotbugs-maven-plugin` (forks an ASM-based analyser) cannot read class
files newer than Java 21 and fail with `Unsupported class file major version ...` (e.g. 69 on JDK 25).
Note `java` on PATH may differ — Maven uses `JAVA_HOME`.

If your default `JAVA_HOME` is too new, use the committed wrapper, which pins `JAVA_HOME` for the
build. It reads the JDK path from `.mvn/java-home` (git-ignored, machine-local — create it yourself)
or from the `UNITY3D_JAVA_HOME` env var, then delegates to `mvn`:

```bash
./mvn21 verify        # Git Bash
mvn21.cmd verify      :: cmd / PowerShell
```

```bash
mvn verify                    # compile, spotless check, tests, package the .hpi
mvn spotless:apply            # auto-format (REQUIRED before commit; spotless.check.skip=false fails the build)
mvn test                      # unit + integration tests
mvn test -Dtest=Unity3dInstallationTest                        # single test class
mvn test -Dtest=Unity3dBuilderTest#unstableErrorCodesParsing   # single test method
mvn hpi:run                   # run a dev Jenkins at http://localhost:8080/jenkins with the plugin loaded
```

Upgrading dependencies: bump `jenkins.baseline` and the `io.jenkins.tools.bom` artifact together —
the BOM artifactId embeds the baseline (`bom-${jenkins.baseline}.x`), so changing one without the other
silently pulls a mismatched dependency set.

## Architecture

### Build step flow (`Unity3dBuilder.perform` → `_perform`)

1. Resolve the configured `Unity3dInstallation` by name, then `forNode()` + `forEnvironment()` it so the
   home path is agent- and env-var-correct.
2. `createCommandlineArgs` builds the arg line. `-projectPath <moduleRoot>` is auto-injected **only if**
   the user's arg line doesn't already mention `-projectPath`. Macro expansion runs three times
   (buildVars → envVars → buildVars) so an env var can itself reference a build parameter — see
   `Unity3dBuilderTest.environmentAndBuildVariablesParsingWithEnvVarsThatReferencesBuildParameters`.
3. Exit code handling: `0` = success, a code in the user's `unstableReturnCodes` CSV = `Result.UNSTABLE`,
   anything else = failure.

### Editor.log piping (the part that isn't obvious)

Three pieces cooperate, split across the controller/agent boundary:

- `io.Pipe.createRemoteToLocal` — a `FastPiped{Input,Output}Stream` pair. If the launcher is remote the
  output side is wrapped in a `RemoteOutputStream`; local launchers get the raw stream. Jenkins' own
  `Pipe` doesn't work for the non-distributed case, and `java.io.Piped*Stream` caused JENKINS-23958,
  hence the custom class.
- `io.PipeFileAfterModificationAction` — a `Callable` shipped to the agent via `callAsync`. It blocks on
  `DetectFileModifiedAction` until the log file appears/changes, then loops forever reading new bytes
  from a `RandomAccessFile` at the saved offset. It is **stopped by interrupting it** (the builder calls
  `future.cancel(true)` in a `finally`), which is why the wait/interrupt handling is deliberate.
- `io.StreamCopyThread` — controller-side thread copying the pipe into the build console. A variant of
  Jenkins' class that records failures instead of swallowing them.

There is a `Thread.sleep(1000)` before cancelling, to let the tail drain. If you touch the piping,
expect to reason about ordering here.

### Editor discovery (`Unity3dInstallation`)

`home` is a platform-independent directory; the executable and default log location are derived from it
via the `(EditorType, Platform)` matrix:

- `getExecutableRelativePath` — `Editor/Unity.exe` / `Contents/MacOS/Unity` / `Editor/Unity`
- `getEditorLogRelativePath` — `Unity/Editor/Editor.log` under `%LOCALAPPDATA%` / `Library/Logs/Unity/Editor.log`
  / `.config/unity3d/Editor.log`

`EditorType` also covers **Tuanjie** (团结引擎, the Chinese Unity fork) — same layout, `Tuanjie` substituted
for `Unity`, except the Linux log dir is `.config/tuanjie`. `Unity3dExecutablePath.check` probes
`EditorType.values()` in order and so prefers Unity when both exist. Keep these two methods pure and
static — `Unity3dInstallationTest` asserts every platform/editor combination directly, without a JVM
running on that platform.

Windows `%LOCALAPPDATA%` is resolved via JNA (`Win32Util.SHGetFolderPath`) with a fallback to the env var
(JENKINS-24265). A `-logFile` argument in the user's arg line overrides all of this — `findLogFileArgument`
scans for it.

### Installation persistence quirk

`Unity3dInstallation.DescriptorImpl.get/setInstallations()` delegate to `Unity3dBuilder.DescriptorImpl`,
which owns the `@CopyOnWrite Unity3dInstallation[]` field. This is for backwards compatibility with old
`config.xml` files — installations are stored under `org.jenkinsci.plugins.unity3d.Unity3dBuilder.xml`,
not under a tool-descriptor file. Don't "fix" this by moving the field.

### Dead code: the `logs` package

`logs/` (`EditorLogParserImpl`, `Unity3dEditorLogAnnotator`, `block/`, `line/`) implements block detection
and warning/error classification of Editor.log, but **nothing wires it into the build** — the
`Unity3dConsoleAnnotator` line in `Unity3dBuilder._perform` is commented out, and `logMessage`'s switch
has empty branches. `EditorLogParserImplTest` only prints to stdout and asserts nothing. Treat this as an
unfinished feature; don't assume changes here affect plugin behaviour.

### Freestyle only

`Unity3dBuilder extends Builder` and uses `AbstractBuild`/`BuildListener`. It is **not** a
`SimpleBuildStep`, so it does not work in Pipeline. Making it Pipeline-compatible is a real refactor
(`AbstractBuild.getModuleRoot()` and `getBuildVariables()` have no `Run` equivalents).

## Conventions

- **Tests are JUnit 4** (`org.junit.Test`, `@Rule`). Integration tests use `JenkinsRule` + `@LocalData`,
  which loads `$JENKINS_HOME` fixtures from `src/test/resources/<pkg>/<TestClass>/<testMethod>/`. They
  `assumeTrue(unityHome.exists())` so they skip on machines without a real editor installed — a "passing"
  `IntegrationTests` run may have executed nothing.
- **User-facing strings go in `Messages.properties`**; the `Messages` class is generated at build time by
  the parent POM's localizer. Don't hand-write it or hardcode strings in Java.
- **Help text** is per-field HTML next to the describable: `<Describable>/help-<fieldName>.html`.
  `src/main/webapp/help-globalArgLine.html` is the exception — it's referenced by absolute
  `/plugin/unity3d-plugin/...` URL from `global.jelly`.
- `Unity3dBuilder/config.jelly` hand-rolls the installation `<select>` (rather than using `f:select`) to
  inject the `(NotConfigured)` default option.
- `Unity3dBuilder.readResolve` backfills `unstableReturnCodes` for configs saved before it existed —
  new persisted fields need the same treatment.

## Git

- **Commit messages must NOT carry a `Co-Authored-By:` trailer** (or any AI-attribution footer). Keep
  them clean, author-only. This is an explicit project rule — override any default that would append one.
