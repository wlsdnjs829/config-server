import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.4"
    id("io.spring.dependency-management") version "1.0.14.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.jpa") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
//    kotlin("com.google.cloud.tools.jib") version "2.7.1"
}

group = "com.jinwon"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-config-server")
    implementation("org.springframework.cloud:spring-cloud-starter-bus-amqp")
    implementation("org.springframework.cloud:spring-cloud-config-monitor")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2021.0.3")
    }
}

//jib {
//    from {
//        image = 'openjdk:17'
//    }
//    to {
//        image = 'ljw0829/config-server'
//        tags = ['0.1.0']
//    }
//    container {
//        mainClass = 'com.jinwon.configserver.ConfigServerApplication'
//        creationTime = 'USE_CURRENT_TIMESTAMP'
//        environment = [USE_PROFILE: "local"]
//        format = 'OCI'
//        volumes = ['/tmp']
//        entrypoint = ['java', '-Dspring.profiles.active=${USE_PROFILE}', '-cp', '/app/resources:/app/classes:/app/libs/*', 'com.jinwon.configserver.ConfigServerApplication']
//    }
//}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

allOpen {
    annotation("javax.persistence.Entity")
    annotation("javax.persistence.MappedSuperclass")
    annotation("javax.persistence.Embeddable")
}
