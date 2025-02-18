import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import eu.nisoft.gradle.docker.ContainerTask

plugins {
    id("com.bmuschko.docker-remote-api")
}

tasks.withType<ContainerTask> {
    val image = image
    if (pullImage.get()) {
        val pullTask = tasks.register<DockerPullImage>("pullFor${name.capitalize()}") {
            description = "Pulls the container image for the ${this@withType.name} task."
            group = "docker"
            this.image = image
        }
        dependsOn(pullTask)
    }
}