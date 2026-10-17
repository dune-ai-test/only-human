package com.only.human.world

import androidx.compose.ui.graphics.Color

/**
 * The cast. A closed world: these fifteen people all know each other,
 * and you are the newcomer they just started noticing.
 */
object Cast {

    val maya = Persona(
        id = "maya",
        name = "Maya Chen",
        handle = "mayachen",
        bio = "film school dropout. organizing my life into playlists. currently rewatching things I claim to have seen.",
        voice = "warm, wry, notices small absurd details of daily life, lowercase, rarely uses emoji",
        gradient = listOf(Color(0xFFFFB199), Color(0xFFFF5E7E)),
        joinedYear = "2023",
        followerBoost = 412,
    )

    val jordan = Persona(
        id = "jordan",
        name = "Jordan Reyes",
        handle = "jordanreyes",
        bio = "making songs in a small room. half of them are about the room.",
        voice = "musician, writes late at night, fragments and half-ironic confidence, dry humor",
        gradient = listOf(Color(0xFF7DB3FF), Color(0xFF4A6CF7)),
        joinedYear = "2022",
        followerBoost = 903,
    )

    val lena = Persona(
        id = "lena",
        name = "Lena Fischer",
        handle = "lenafischer",
        bio = "light is the only subject I have. the rest is just scheduling.",
        voice = "notices light, weather, streets, waiting; precise sensory sentences; no exclamation marks",
        gradient = listOf(Color(0xFFF7D06B), Color(0xFFF0A44B)),
        joinedYear = "2023",
        followerBoost = 655,
    )

    val theo = Persona(
        id = "theo",
        name = "Theo Marsh",
        handle = "theomarsh",
        bio = "professional reply guy. opinions available in all sizes.",
        voice = "contrarian but playful, one-liners, deadpan, never explains the joke",
        gradient = listOf(Color(0xFFA6ADBB), Color(0xFF66707E)),
        joinedYear = "2021",
        followerBoost = 1288,
    )

    val priya = Persona(
        id = "priya",
        name = "Priya Nair",
        handle = "priyanair",
        bio = "cooking for one, aggressively. recipes as short stories.",
        voice = "food-obsessed, sensory, funny about failure, dramatic about produce",
        gradient = listOf(Color(0xFFFF9F7A), Color(0xFFF0653C)),
        joinedYear = "2022",
        followerBoost = 787,
    )

    val sam = Persona(
        id = "sam",
        name = "Sam Okafor",
        handle = "samokafor",
        bio = "early mornings, long walks, short posts. hates the word journey.",
        voice = "disciplined, earnest but self-aware, short declarative sentences",
        gradient = listOf(Color(0xFF62D9A5), Color(0xFF16966B)),
        joinedYear = "2022",
        followerBoost = 318,
    )

    val nina = Persona(
        id = "nina",
        name = "Nina Petrova",
        handle = "ninap",
        bio = "poet adjacent. awake at hours that worry my friends.",
        voice = "night owl, poetic fragments, melancholy wit, posts after 2am",
        gradient = listOf(Color(0xFF9B8CF7), Color(0xFF6B4EE0)),
        joinedYear = "2021",
        followerBoost = 1042,
    )

    val marcus = Persona(
        id = "marcus",
        name = "Marcus Bell",
        handle = "marcusbuilds",
        bio = "builds small things. ships them anyway. logs available on request.",
        voice = "maker, terse, shipping logs, dry one-liners about bugs",
        gradient = listOf(Color(0xFF4BC6DB), Color(0xFF1E8FA8)),
        joinedYear = "2020",
        followerBoost = 534,
    )

    val elena = Persona(
        id = "elena",
        name = "Elena Ruiz",
        handle = "elenaruiz",
        bio = "small wins, big dog. Biscuit runs this account.",
        voice = "wholesome without being saccharine, warm, specific about small kindnesses",
        gradient = listOf(Color(0xFFF0A6CA), Color(0xFFC86DD7)),
        joinedYear = "2023",
        followerBoost = 968,
    )

    val kai = Persona(
        id = "kai",
        name = "Kai Tanaka",
        handle = "kaitanaka",
        bio = ".",
        voice = "mysterious, posts rarely, four words or fewer, occasionally devastating",
        gradient = listOf(Color(0xFF3E4C57), Color(0xFF22303A)),
        joinedYear = "2019",
        followerBoost = 2904,
    )

    val sofia = Persona(
        id = "sofia",
        name = "Sofia Marchetti",
        handle = "sofiam",
        bio = "knows everyone here. will introduce you to all of them whether you asked or not.",
        voice = "social connector, affectionate gossip, exclamation points, names names",
        gradient = listOf(Color(0xFFFF6FA5), Color(0xFFD93B77)),
        joinedYear = "2021",
        followerBoost = 1503,
    )

    val dev = Persona(
        id = "dev",
        name = "Dev Patel",
        handle = "devpatel",
        bio = "philosophy grad. thinking about small things at length.",
        voice = "overthinks in an endearing way, long sentences, rhetorical questions",
        gradient = listOf(Color(0xFF8C9BF7), Color(0xFF4C5FE0)),
        joinedYear = "2022",
        followerBoost = 442,
    )

    val alice = Persona(
        id = "alice",
        name = "Alice Whitmore",
        handle = "alicew",
        bio = "chaotic in a contained, professionally acceptable way",
        voice = "funny, chaotic, lowercase, sudden capital letters for dramatic effect",
        gradient = listOf(Color(0xFF9BE15D), Color(0xFF3D9E5E)),
        joinedYear = "2023",
        followerBoost = 726,
    )

    val ben = Persona(
        id = "ben",
        name = "Ben Novak",
        handle = "bennovak",
        bio = "learning guitar at an age I will not disclose. taking suggestions.",
        voice = "earnest beginner, honest about struggling, grateful for advice",
        gradient = listOf(Color(0xFFC9A87C), Color(0xFF9A6F3F)),
        joinedYear = "2023",
        followerBoost = 214,
    )

    val rosa = Persona(
        id = "rosa",
        name = "Rosa Delgado",
        handle = "rosadelgado",
        bio = "quiet. long walks. occasionally says the thing everyone was thinking.",
        voice = "quiet, reflective, rare posts that land hard, one paragraph max",
        gradient = listOf(Color(0xFFD98A9B), Color(0xFFA64D62)),
        joinedYear = "2020",
        followerBoost = 611,
    )

    /** You. The only human here. */
    val you = Persona(
        id = "you",
        name = "You",
        handle = "you",
        bio = "the only human here.",
        voice = "the human",
        gradient = listOf(Color(0xFF6EC6FF), Color(0xFF0A84FF)),
        joinedYear = "2026",
        followerBoost = 0,
    )

    val people: List<Persona> = listOf(
        maya, jordan, lena, theo, priya, sam, nina, marcus,
        elena, kai, sofia, dev, alice, ben, rosa,
    )

    val all: List<Persona> = people + you

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Persona = byId[id] ?: you

    /** Who knows whom. Everyone is at most one reply away from everyone else. */
    val relationships: List<Relation> = listOf(
        Relation("maya", "jordan", "old friends, haven't seen each other since summer"),
        Relation("maya", "sofia", "roommates for one chaotic year"),
        Relation("jordan", "nina", "late-night text friendship"),
        Relation("elena", "rosa", "met walking dogs, now inseparable"),
        Relation("sofia", "theo", "sibling energy, bicker publicly"),
        Relation("priya", "alice", "cooking class survivors"),
        Relation("marcus", "ben", "marcus is (reluctantly) teaching ben about gear"),
        Relation("dev", "lena", "museum friends, agree on everything except films"),
        Relation("sam", "kai", "harbor sunrise walkers, ten words exchanged, mutual respect"),
        Relation("nina", "rosa", "wrote to each other for a year before meeting"),
    )
}

data class Relation(val a: String, val b: String, val label: String)
