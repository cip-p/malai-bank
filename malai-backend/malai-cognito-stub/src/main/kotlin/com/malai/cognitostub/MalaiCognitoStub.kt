package com.malai.cognitostub

fun main() {
    val server = MalaiCognitoStubServer(StubSettings.fromEnvironment()).start()
    Runtime.getRuntime().addShutdownHook(Thread(server::stop))
}
