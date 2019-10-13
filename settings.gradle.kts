rootProject.name = "vertx-kuickstart"

val projects = listOf("vertx-kuickstart-core")

for (project: String in projects) {
  include(project)
  project(":$project").projectDir = File(settingsDir, "../$project")
}