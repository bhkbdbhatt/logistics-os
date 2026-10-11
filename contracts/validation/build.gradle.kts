plugins {
    `java`
}

description = "Schema-validation tooling for contracts/. Parse-all + registry + namespace reconcile gate."

dependencies {
    implementation(libs.avro)
}

val validateSchemas by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Parse every .avsc in a single Schema.Parser, reconcile payload-registry.json, and check payload namespaces are referenced."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.logisticsos.build.ContractValidator")
    args(rootProject.layout.projectDirectory.dir("contracts").asFile.absolutePath)
}