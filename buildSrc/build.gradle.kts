plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinpoet)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
