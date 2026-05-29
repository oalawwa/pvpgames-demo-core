import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    // Bundles runtime libraries (HikariCP, JDBC drivers) into the final jar.
    // (Shadow 8.3.x targets Gradle 8.3+; pairs with the 8.11.1 wrapper used here.)
    id("com.gradleup.shadow") version "8.3.10"
}

group = property("group") as String
version = property("version") as String

// --- Versions pulled from gradle.properties so they live in one place ---
val paperApiVersion = property("paperApiVersion") as String
val placeholderApiVersion = property("placeholderApiVersion") as String
val luckpermsApiVersion = property("luckpermsApiVersion") as String
val citizensApiVersion = property("citizensApiVersion") as String
val hikariCpVersion = property("hikariCpVersion") as String
val mysqlConnectorVersion = property("mysqlConnectorVersion") as String
val sqliteJdbcVersion = property("sqliteJdbcVersion") as String

java {
    // Java 21 toolchain. Gradle will auto-download a matching JDK if needed.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    // Paper API + most plugin APIs
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    // PlaceholderAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") { name = "placeholderapi" }
    // Citizens
    maven("https://repo.citizensnpcs.co/") { name = "citizens" }
    // Hosts net.byteflux:libby, a runtime helper Citizens depends on (safety net so the
    // Citizens API resolves even though we exclude its transitive deps below).
    maven("https://repo.minebench.de/") { name = "minebench" }
    // DecentHolograms, CombatLogX, TAB, DeluxeMenus are accessed reflectively/at runtime,
    // so they do NOT need compile-time dependencies. See DULC docs in README.
    maven("https://jitpack.io") { name = "jitpack" }
    // Sonatype (LuckPerms api lives on Maven Central, kept here as a safety net)
    maven("https://oss.sonatype.org/content/repositories/snapshots/") { name = "sonatype" }
}

dependencies {
    // ---- provided (not shaded): the server already has these on the classpath ----
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("me.clip:placeholderapi:$placeholderApiVersion")
    compileOnly("net.luckperms:api:$luckpermsApiVersion")
    // Citizens API only. We compile against Citizens' classes but never use its internal
    // runtime libraries (like net.byteflux:libby), so exclude transitive deps to keep the
    // build lean and avoid resolving libraries that aren't on common repositories.
    compileOnly("net.citizensnpcs:citizens-main:$citizensApiVersion") {
        isTransitive = false
    }

    // ---- shaded (bundled into our jar so we don't ask servers to install them) ----
    implementation("com.zaxxer:HikariCP:$hikariCpVersion")
    implementation("com.mysql:mysql-connector-j:$mysqlConnectorVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")

    // ---- annotations / nullability ----
    compileOnly("org.jetbrains:annotations:24.1.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        // Inject the project version into plugin.yml at build time (so it always matches
        // gradle.properties). expand() does Groovy-style ${...} templating on the matched file.
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        // Relocate bundled libs to avoid clashing with other plugins on the server.
        val base = "gg.pvpgames.demo.libs"
        relocate("com.zaxxer.hikari", "$base.hikari")
        relocate("com.mysql", "$base.mysql")
        relocate("org.sqlite", "$base.sqlite")
        relocate("com.google.protobuf", "$base.protobuf") // pulled in by the mysql connector
        // Merge META-INF/services so the JDBC drivers register correctly after shading.
        mergeServiceFiles()
        // NOTE: we deliberately do NOT call minimize(). The JDBC drivers and parts of HikariCP
        // are loaded reflectively/by service files; minimize() can strip them and cause runtime
        // ClassNotFound errors. The extra jar size is negligible for a demo and guarantees the
        // plugin just works.
    }

    // Make `gradle build` produce the shaded jar as the primary artifact.
    build {
        dependsOn(named("shadowJar"))
    }

    jar {
        // Disable the thin jar; shadowJar is what we ship.
        enabled = false
    }
}
