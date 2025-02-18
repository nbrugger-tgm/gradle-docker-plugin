package eu.nisoft.gradle.docker

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Frame
import org.gradle.api.GradleException
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

abstract class StartContainerTask @Inject constructor(
    providerFactory: ProviderFactory,
    layout: ProjectLayout,
    fileOp: FileOperations
) : ContainerTask(fileOp) {
    @get:OutputFile
    abstract val containerIdFile: RegularFileProperty

    private val existingContainer: Provider<InspectContainerResponse> = providerFactory.provider {
        try {
            dockerClient.inspectContainerCmd(containerName.get()).exec()
        } catch (_: NotFoundException) {
            null
        } catch (e: Exception) {
            throw GradleException("Failed to fetch container with name '${containerName.get()}'", e)
        }
    }

    private val runningContainer: Provider<InspectContainerResponse> = existingContainer.filter {
        it.state.running
    }

    private val previousContainerId = providerFactory.provider {
        containerIdFile.get().asFile.let {
            if (it.exists()) it.readText(Charsets.UTF_8)
            else null
        }
    }

    @Internal
    var containerId: String? = null
        private set

    /**
     * A regex searched for in each log line, occurrence indicates readiness of container
     * if not set no healthchecks are performed
     *
     * This value changing does not re-run the task
     */
    @get:Internal
    abstract val waitForLog: Property<String>

    init {
        outputs.cacheIf("running a container is not cachable") { false }
        outputs.upToDateWhen {
            runningContainer.isPresent && runningContainer.get().id == previousContainerId.orNull
        }
        containerIdFile.convention(layout.buildDirectory.file("containers/${name}"))
    }

    override fun runRemoteCommand() {
        if (runningContainer.isPresent) {
            val runningContainerId = runningContainer.get().id
            logger.lifecycle("stopping outdated container ${runningContainer.get().name ?: runningContainerId}")
            dockerClient.killContainerCmd(runningContainerId).exec()
        }
        if (existingContainer.isPresent) {
            val existingContaienrId = existingContainer.get().id
            logger.lifecycle("removing outdated container ${existingContainer.get().name ?: existingContaienrId}")
            dockerClient.removeContainerCmd(existingContaienrId).exec()
        }

        containerId = createContainer()
        containerIdFile.get().asFile.writeText(containerId!!, Charsets.UTF_8)

        startContainer(containerId!!)
    }

    private fun startContainer(containerId: String) {
        dockerClient.startContainerCmd(containerId).exec()
        logger.lifecycle("Starting container")

        if (waitForLog.isPresent) {
            awaitContainerLog(containerId, Regex(waitForLog.get()))
        }
    }

    private fun awaitContainerLog(containerId: String, logRegex: Regex) {
        logger.quiet("Wait for container to be ready ...")
        val dockerLogCmd = dockerClient.logContainerCmd(containerId)
            .withStdOut(true)
            .withSince(0)
            .withStdErr(true)
            .withFollowStream(true)
        dockerLogCmd.exec(object : ResultCallback.Adapter<Frame>() {
            override fun onNext(logFrame: Frame) {
                val isReady = logFrame.payload.toString(Charsets.UTF_8).contains(logRegex)
                logger.info("[${containerName.get()}] " + logFrame.payload.toString(Charsets.UTF_8).trimEnd('\n'))
                if (isReady) {
                    logger.lifecycle("Container is ready")
                    onComplete()
                }
            }
        }).awaitCompletion()
    }
}

