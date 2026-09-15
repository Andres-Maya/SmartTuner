package com.andres.smarttuner.ai

/** Etiquetas de AudioSet (YAMNet) agrupadas según los instrumentos del afinador. */
internal object YamnetLabels {
    val GUITAR = setOf(
        "Guitar",
        "Electric guitar",
        "Acoustic guitar",
        "Steel guitar, slide guitar",
        "Tapping (guitar technique)",
        "Strum",
    )
    val BASS = setOf("Bass guitar", "Double bass")
    val VIOLIN = setOf("Violin, fiddle")
    val CELLO = setOf("Cello")
    val UKULELE = setOf("Ukulele")

    /** Evidencia de familia sin instrumento concreto (YAMNet no tiene clase "Viola"). */
    val BOWED_FAMILY = setOf("Bowed string instrument", "String section", "Pizzicato")
    val PLUCKED_FAMILY = setOf("Plucked string instrument")

    /** Instrumentos que no son modos del afinador: se agrupan en "Otro". */
    val OTHER_INSTRUMENTS = setOf(
        "Banjo", "Sitar", "Mandolin", "Zither", "Harp",
        "Keyboard (musical)", "Piano", "Electric piano", "Organ", "Electronic organ", "Hammond organ",
        "Synthesizer", "Harpsichord", "Accordion",
        "Drum kit", "Drum", "Snare drum", "Bass drum", "Timpani", "Tabla", "Cymbal", "Percussion",
        "Marimba, xylophone", "Glockenspiel", "Vibraphone", "Steelpan", "Tubular bells",
        "Didgeridoo", "Theremin",
        "Brass instrument", "Trumpet", "Trombone", "French horn",
        "Wind instrument, woodwind instrument", "Flute", "Saxophone", "Clarinet", "Harmonica", "Bagpipes",
        "Singing", "Choir",
    )
}
