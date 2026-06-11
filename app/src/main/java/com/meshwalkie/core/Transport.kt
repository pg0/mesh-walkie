package com.meshwalkie.core

/**
 * A broadcast medium. NearbyTransport (radio) and FakeTransport (tests)
 * both implement this; MeshEngine only ever sees this interface.
 */
interface Transport {
    /** Send bytes to every directly connected link. */
    fun broadcast(bytes: ByteArray)

    /** Register the single receive handler. Called once by MeshEngine. */
    fun onReceive(handler: (ByteArray) -> Unit)
}
