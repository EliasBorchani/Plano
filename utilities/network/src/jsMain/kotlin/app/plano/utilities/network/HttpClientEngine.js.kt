package app.plano.utilities.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual fun httpClientEngine(): HttpClientEngine = Js.create()
