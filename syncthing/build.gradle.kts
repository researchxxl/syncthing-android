import javax.inject.Inject
import org.gradle.process.ExecOperations
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

fun detectPythonBinary(): String {
    val osName = System.getProperty("os.name").lowercase()
    return if (osName.contains("windows")) "python" else "python3"
}

abstract class BuildNativeTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:InputDirectory
    abstract val workingDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Input
    abstract val pythonBinary: Property<String>

    @TaskAction
    fun run() {
        val workDirFile = workingDir.get().asFile

        val env = mapOf(
            "NDK_VERSION" to ndkVersion.get(),
        )

        val fullEnv = System.getenv().toMutableMap().apply {
            putAll(env)

            System.getProperty("http.proxyHost")?.let { host ->
                System.getProperty("http.proxyPort")?.let { port ->
                    this["HTTP_PROXY"] = "http://$host:$port"
                }
            }

            System.getProperty("https.proxyHost")?.let { host ->
                System.getProperty("https.proxyPort")?.let { port ->
                    this["HTTPS_PROXY"] = "http://$host:$port"
                }
            }

            System.getProperty("http.nonProxyHosts")?.let { np ->
                this["NO_PROXY"] = np
            }
        }

        execOps.exec {
            environment(fullEnv)
            workingDir(workDirFile)
            commandLine(pythonBinary.get(), "-u", "./build-syncthing.py")
        }
    }
}

tasks.register<BuildNativeTask>("buildNative") {
    group = "build"
    description = "Builds native Syncthing binaries"

    inputDir.set(layout.projectDirectory.dir("src"))
    workingDir.set(layout.projectDirectory)
    outputDir.set(layout.projectDirectory.dir("../app/src/main/jniLibs"))
    ndkVersion.set(libs.versions.ndk.version)
    pythonBinary.set(detectPythonBinary())
}
