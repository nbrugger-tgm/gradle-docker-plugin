package eu.nisoft.gradle.docker

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Volume
import org.gradle.api.GradleException
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import java.io.File
import java.io.Serializable
import java.util.stream.Collectors

abstract class ContainerTask(
    @Internal val fileOp: FileOperations
) : AbstractDockerRemoteApiTask() {
    @get:Input
    abstract val containerName: Property<String>
    @get:Input
    abstract val image: Property<String>
    @get:Input
    @get:Optional
    abstract val hostname: Property<String>
    @get:Input
    @get:Optional
    abstract val portBindings: ListProperty<String>
    @get:Input
    @get:Optional
    abstract val envVars: MapProperty<String, String>
    @get:Input
    @get:Optional
    abstract val labels: MapProperty<String?, String?>
    @get:Input
    @get:Optional
    abstract val networkAliases: ListProperty<String>
    @get:Input
    @get:Optional
    abstract val entrypoint: ListProperty<String>
    @get:Input
    @get:Optional
    abstract val cmd: ListProperty<String>
    @get:Input
    @get:Optional
    abstract val volumes: ListProperty<String>
    @get:Nested
    @get:Optional
    protected abstract val volumeBinds: MapProperty<String, VolumeBindPath>
    @get:Input
    abstract val pullImage: Property<Boolean>
    @get:Input
    @get:Optional
    abstract val network: Property<String>

    @get:Input
    protected val imageReferenceId = image.map {
        dockerClient.inspectImageCmd(it).exec().id
    }

    class VolumeBindPath(@InputFile val file: File) : Serializable

    init {
        pullImage.convention(false)
    }

    fun volumeBind(pathTuple: String) {
        val split = pathTuple.split(":", limit = 2)
        if (split.size != 2) throw GradleException("Volume Bind path '$pathTuple' is not supported, expected format is 'host-path:container-path' (https://docs.docker.com/engine/storage/bind-mounts/#options-for---volume)")
        volumeBind(split[0], split[1])
    }

    fun volumeBind(hostPath: Any, containerPath: String) {
        volumeBinds.put(containerPath, VolumeBindPath(fileOp.file(hostPath)))
    }

    fun volumeBind(hostPath: Provider<*>, containerPath: String) {
        volumeBinds.put(containerPath, hostPath.map { VolumeBindPath(fileOp.file(it)) })
    }


    protected fun createContainer(): String {
        val createCmd = dockerClient.createContainerCmd(imageReferenceId.get())
        setContainerCommandConfig(createCmd)
        val id = createCmd.exec().id
        logger.lifecycle("Created new container ${containerName.getOrElse(id)}")
        return id
    }

    private fun setContainerCommandConfig(containerCommand: CreateContainerCmd) {
        if (containerName.isPresent) {
            containerCommand.withName(containerName.get())
        }

        if (envVars.isPresent && !envVars.get().isEmpty()) {
            containerCommand.withEnv(
                envVars.get().entries.stream()
                    .map { entry -> entry.key + "=" + entry.value }
                    .collect(Collectors.toList()))
        }

        if (volumes.getOrNull() != null && !volumes.get().isEmpty()) {
            val createdVolumes: MutableList<Volume?> =
                volumes.get().stream().map { path: String? -> Volume(path) }.collect(Collectors.toList())
            containerCommand.withVolumes(createdVolumes)
        }
        if (cmd.isPresent && !cmd.get().isEmpty()) {
            containerCommand.withCmd(cmd.get())
        }

        if (entrypoint.isPresent && !entrypoint.get().isEmpty()) {
            containerCommand.withEntrypoint(entrypoint.get())
        }

        if (networkAliases.isPresent && !networkAliases.get().isEmpty()) {
            containerCommand.withAliases(networkAliases.get())
        }

        if (image.isPresent) {
            containerCommand.withImage(image.get())
        }

        if (portBindings.isPresent && !portBindings.get().isEmpty()) {
            val createdPortBindings: MutableList<PortBinding?> = portBindings.get().stream()
                .map { serialized -> PortBinding.parse(serialized) }.collect(Collectors.toList())
            containerCommand.hostConfig.withPortBindings(createdPortBindings)
        }

        if (labels.isPresent && !labels.get().isEmpty()) {
            containerCommand.withLabels(labels.get())
        }
        if (volumeBinds.isPresent && !volumeBinds.get().isEmpty()) {
            val createdBinds: MutableList<Bind> = volumeBinds.get().entries.stream()
                .map { Bind(it.value.file.absolutePath, Volume(it.key)) }
                .collect(Collectors.toList())
            containerCommand.hostConfig.withBinds(createdBinds)
        }
        val hostname = hostname.orElse(containerName)
        if (hostname.isPresent) {
            containerCommand.withHostName(hostname.get())
        }

        if (network.isPresent) {
            containerCommand.hostConfig.withNetworkMode(network.get())
        }
    }
}