package eu.nisoft.gradle.docker

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.WaitResponse
import org.gradle.api.GradleException
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.*
import org.gradle.api.tasks.Optional
import javax.inject.Inject


abstract class RunContainerTask @Inject constructor(
    fileOp: FileOperations
) : ContainerTask(fileOp) {
    @get:Optional
    abstract override val containerName: Property<String>

    init {
        onNext { logger.lifecycle(this.toString().trimEnd('\n')) }
    }

    override fun runRemoteCommand() {
        val containerId = createContainer()
        try {
            startContainer(containerId)
            forwardContainerLogs(containerId)
            awaitContainerShutdown(containerId)
        } finally {
            removeContainer(containerId)
        }
    }


    private fun removeContainer(containerId: String) {
        dockerClient.removeContainerCmd(containerId).exec()
        logger.info("Removing residual container $containerId")
    }

    private fun awaitContainerShutdown(containerId: String) {
        dockerClient.waitContainerCmd(containerId)
            .exec(object : ResultCallback.Adapter<WaitResponse>() {
                override fun onNext(wait: WaitResponse) {
                    if (wait.statusCode != 0) {
                        onError(GradleException("Container $containerId failed (exit code ${wait.statusCode})"))
                    } else {
                        logger.info("Container $containerId finished")
                        onComplete()
                    }
                }
            }).awaitCompletion()
    }

    private fun forwardContainerLogs(containerId: String) {
        dockerClient.logContainerCmd(containerId).withStdOut(true).withStdErr(true)
            .withFollowStream(true).exec(
                object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(logFrame: Frame) {
                        nextHandler.execute(logFrame.payload.toString(Charsets.UTF_8))
                    }
                }
            )
    }

    private fun startContainer(containerId: String) {
        dockerClient.startContainerCmd(containerId).exec()
        logger.info("Starting container")
    }

}