import com.beust.kobalt.*
import com.beust.kobalt.plugin.packaging.*
import com.beust.kobalt.plugin.application.*
import com.beust.kobalt.plugin.kotlin.*

val bs = buildScript {
    repos("http://dl.bintray.com/kotlin/kotlin-eap-1.1")
}

val kotlinVersion = "1.1.0"

val p = project {

    name = "farm-poll-bot"
    group = "me.reckter"
    artifactId = name
    version = "1.0-SNAPSHOT"

    sourceDirectories {
        path("src/main/kotlin")
    }

    sourceDirectoriesTest {
        path("src/test/kotlin")
    }

    dependencies {
//        compile("com.beust:jcommander:1.48")
        compile("me.reckter:telegramBotApi:0.16.9")
        compile("org.jetbrains.kotlin:kotlin-stdlib:1.1.0")
        compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.12")
        compile("org.litote.kmongo:kmongo:3.3.5")
        compile("org.slf4j:slf4j-simple:1.7.21")
    }

    dependenciesTest {
        compile("org.testng:testng:6.10")
        compile("org.jetbrains.kotlin:kotlin-test:1.1.0")

    }

    assemble {
        jar {

        }
    }

    application {
        mainClass = "me.reckter.bot.MainKt"
    }


}
