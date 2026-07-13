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

/**
 * A transport that can tell the engine which underlying link each packet
 * arrived on (BLE mesh vs internet server). [CompositeTransport] implements
 * this; plain leaf transports don't, and MeshEngine treats their traffic as
 * [Route.MESH]. Used only to label who/what is reachable over which path -
 * it does not affect flooding or delivery.
 */
interface RoutedTransport : Transport {
    fun onReceiveRouted(handler: (ByteArray, Route) -> Unit)
}
