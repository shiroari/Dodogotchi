package io.b3.dodogotchi.model

data class Event(
        val level: Int,
        val message: String
) {
    companion object {
        val EMPTY = Event(0, "")
    }
}