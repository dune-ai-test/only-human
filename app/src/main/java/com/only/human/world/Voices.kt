package com.only.human.world

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * Their offline voices — the lines the fifteen actually write when no LLM is
 * configured. Bucketed by time of day, because sam is not awake at 3am and
 * nina is only properly awake at 3am.
 *
 * When the AI engine lands, this becomes the fallback rather than the whole
 * story. Write every line as if the persona wrote it themselves.
 */
object Voices {

    enum class Part { Morning, Afternoon, Evening, Late }

    fun partOf(at: Long): Part {
        val hour = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).hour
        return when (hour) {
            in 5..10 -> Part.Morning
            in 11..16 -> Part.Afternoon
            in 17..22 -> Part.Evening
            else -> Part.Late
        }
    }

    /** A post by [id], written for the hour [at] falls in. */
    fun post(id: String, at: Long, rng: Random): String {
        val bank = lines[id] ?: return "still here."
        val wanted = bank[partOf(at)]
        val pool = if (!wanted.isNullOrEmpty()) wanted else bank.values.first { it.isNotEmpty() }
        return pool.random(rng)
    }

    /** A short reaction to something you posted. */
    fun reply(rng: Random): String = replies.random(rng)

    /** A post that mentions @you. */
    fun mention(rng: Random): String = mentions.random(rng)

    /** A short reply back, when a persona answers you directly. */
    fun replyBack(id: String, rng: Random): String {
        val pool = backReplies[id]
        return pool?.random(rng) ?: replies.random(rng)
    }

    /** A DM out of the blue — the first private message they ever send you. */
    fun dmOpen(id: String, rng: Random): String {
        val pool = dmOpens[id]
        return pool?.random(rng) ?: genericDmOpens.random(rng)
    }

    /** A short DM back, when you message one of them. */
    fun dmReply(id: String, rng: Random): String {
        val pool = dmReplies[id]
        return pool?.random(rng) ?: genericDmReplies.random(rng)
    }

    private val genericDmOpens = listOf(
        "hey. you're the new neighbor, right?",
        "hi. just checking you actually exist.",
        "hello. this is me sliding into your DMs, but politely.",
    )

    private val genericDmReplies = listOf(
        "ha. fair.",
        "mmhm. go on.",
        "yeah, i hear that.",
        "noted. keep talking.",
        "okay, i wasn't expecting a good answer.",
    )

    /** Openers and replies per persona — same person as their posts, quieter room. */
    private val dmOpens: Map<String, List<String>> = mapOf(
        "sofia" to listOf("OK HI. i was told to be normal and i am choosing not to be!! how are you finding it here so far??"),
        "maya" to listOf("hey. you're the new one. sofia is about to make this very weird, i apologize in advance."),
        "jordan" to listOf("yo. quick test — does this app actually have DMs or am i writing into the void"),
        "lena" to listOf("the light where you are must be nice today. hello."),
        "theo" to listOf("welcome to my DMs. it's like my posts but with less moderation."),
        "priya" to listOf("hi!! urgent question: how do you feel about dal. answers will be judged."),
        "sam" to listOf("hey. early here. you're the new one, right?"),
        "nina" to listOf("you exist. i checked. weird how that works at night."),
        "marcus" to listOf("hi. quick log entry: new neighbor detected. that's all."),
        "elena" to listOf("hii! biscuit says hi (i'm translating). welcome :)."),
        "kai" to listOf("hello."),
        "dev" to listOf("hi. i've been thinking about why new neighbors make everyone nervous. anyway. hi."),
        "alice" to listOf("HI. urgent. greg the plant and i have a bet going about you. no further questions!!"),
        "ben" to listOf("hey! it's a good place here. quiet. that's the pitch."),
        "rosa" to listOf("hello. elena says you're kind. i trust her about people."),
    )

    private val dmReplies: Map<String, List<String>> = mapOf(
        "sofia" to listOf("ok you can't just SAY that and leave!! details. now.", "this is going in the group chat. lovingly."),
        "maya" to listOf("hm. yeah. i think about that too, actually.", "ok that's a great point and i'm mildly annoyed about it"),
        "jordan" to listOf("ha. noted.", "ok that's a lyric. don't fight me on it."),
        "lena" to listOf("yes. exactly that.", "i'll look for it tomorrow."),
        "theo" to listOf("bold of you to be correct on the first try.", "where did you even come from. no one's this reasonable."),
        "priya" to listOf("correct answer.", "ok you're getting a recipe and you cannot stop me."),
        "sam" to listOf("good answer.", "agreed. keeping it simple is underrated."),
        "nina" to listOf("you write like someone who sleeps. teach me.", "keeping this one. filed under 3am."),
        "marcus" to listOf("correct. noted in the log.", "that's the whole comment. thanks."),
        "elena" to listOf("biscuit approves of this message.", "that's really kind, honestly."),
        "kai" to listOf("yes.", "hm."),
        "dev" to listOf("that's the whole thesis, actually.", "you skipped two chapters of my reasoning and landed exactly on the end."),
        "alice" to listOf("ohmigod VALID", "greg was RIGHT???"),
        "ben" to listOf("oh that actually helps, thanks.", "man. yeah. that's the whole thing, isn't it."),
        "rosa" to listOf("mm. yes.", "that's the kindest way anyone's put it."),
    )

    /** One-line replies, in voice, for when you talk back. */
    private val backReplies: Map<String, List<String>> = mapOf(
        "sofia" to listOf("OK this. this is exactly what i mean!!"),
        "maya" to listOf("noted. filed under 'things you're right about'."),
        "jordan" to listOf("okay that's a bar. stealing it for a lyric."),
        "lena" to listOf("yes. that exact thing."),
        "theo" to listOf("correct. annoyingly."),
        "priya" to listOf("okay NOW i'm hungry and it's your fault."),
        "sam" to listOf("agreed. and short. respect."),
        "nina" to listOf("you say things plain. i admire that."),
        "marcus" to listOf("correct. shipping this opinion."),
        "elena" to listOf("this genuinely made my morning."),
        "kai" to listOf("yes."),
        "dev" to listOf("see, this is the part everyone else skips. good."),
        "alice" to listOf("i have THOUGHTS and they're all yes."),
        "ben" to listOf("man. yeah. that's the whole thing, isn't it."),
        "rosa" to listOf("this. quietly, this."),
    )

    private val replies = listOf(
        "this is the most honest thing i've read all week.",
        "ok but you didn't have to say it like that.",
        "screenshotting this. for reasons.",
        "the way i needed to read this today.",
        "going to be thinking about this on my walk.",
        "this is either a warning or advice. either way, noted.",
        "you can't just post something this true casually.",
        "i have questions. good ones. later.",
        "read this twice. yep.",
        "putting this in the group chat.",
    )

    private val mentions = listOf(
        "still thinking about what @you said. refusing to elaborate.",
        "the new neighbor @you has been quiet. too quiet. respect.",
        "someone said something today that reframed my whole week. @you",
        "@you is officially familiar now. this is how it happens.",
        "was going to post something clever. @you already said it better.",
        "@you. that's it. that's the post.",
    )

    private val lines: Map<String, Map<Part, List<String>>> = mapOf(

        "maya" to mapOf(
            Part.Morning to listOf(
                "made coffee with the lights still off. it's becoming a whole personality.",
                "every fridge hums in a different key. mine hums in shame.",
            ),
            Part.Afternoon to listOf(
                "reorganized my watchlist by emotional damage level. it went alphabetical again by accident.",
                "cleaned my desk by relocating everything to the floor. progress is a spectrum.",
            ),
            Part.Evening to listOf(
                "the playlist was supposed to be 40 minutes. it's now 3 hours and titled 'the playlist'.",
                "made a list of films to rewatch and accidentally watched none of them. classic.",
            ),
            Part.Late to listOf(
                "rewatching something i claim to have seen. i have not seen it.",
            ),
        ),

        "jordan" to mapOf(
            Part.Morning to listOf(
                "woke up humming yesterday's chorus. it's a chord progression from a detergent ad. keeping it.",
            ),
            Part.Afternoon to listOf(
                "mixed for six hours. the kick now sounds like a door closing in another building.",
            ),
            Part.Evening to listOf(
                "wrote the bridge. it's the chorus, quieter. don't tell the chorus.",
            ),
            Part.Late to listOf(
                "2am. the song is finally honest. the neighbors are finally angry.",
                "recorded a take with the window open. the traffic is in the mix now. it's helping.",
            ),
        ),

        "lena" to mapOf(
            Part.Morning to listOf(
                "the fog made the streetlamps look like they were thinking.",
                "6:40. the light came in sideways and everything on my desk briefly mattered.",
            ),
            Part.Afternoon to listOf(
                "waited for a bus i wasn't taking because the light on the shelter was perfect.",
            ),
            Part.Evening to listOf(
                "sunset did the thing where the street looks like a photo someone forgot to develop.",
            ),
            Part.Late to listOf(
                "the city at night is lit windows and guesses about who's inside.",
            ),
        ),

        "theo" to mapOf(
            Part.Morning to listOf(
                "coffee shops play jazz for people who won't buy anything else. there's a lesson there.",
                "i don't do mornings. i do early afternoons with enthusiasm.",
            ),
            Part.Afternoon to listOf(
                "lunch is a made-up meal for people who can't commit.",
                "brunch is a meeting with better lighting.",
            ),
            Part.Evening to listOf(
                "was going to cook tonight, then remembered i'm a person with options.",
                "ordered the same thing as last time. consistency is a choice.",
            ),
            Part.Late to listOf(
                "sleep is just shutting down without asking the user.",
                "midnight is peer pressure from the clock.",
            ),
        ),

        "priya" to mapOf(
            Part.Morning to listOf(
                "the tomatoes at the market were so good i called my mother. she asked if i was okay.",
                "boiled the chai over once. the kitchen now smells like a decision.",
            ),
            Part.Afternoon to listOf(
                "lunch was a handful of almonds over the sink. the almonds deserved better.",
                "found a cardamom pod in an old coat pocket. peak intel from past me.",
            ),
            Part.Evening to listOf(
                "roasted a chicken so evenly i stared at it like it owed me money. then i ate it in twelve minutes.",
                "the dal was perfect tonight. no, i will not explain. i don't know either.",
            ),
            Part.Late to listOf(
                "midnight fridge. stood there like the fridge has answers. the answer was pickles.",
            ),
        ),

        "sam" to mapOf(
            Part.Morning to listOf(
                "5:12. out the door before the brain can object. foolproof.",
                "cold morning. the harbor was glass. stood there ten minutes. worth the alarm.",
            ),
            Part.Afternoon to listOf(
                "walk at lunch. same route. new pigeons.",
                "someone left the treadmill at max incline again. respect. or worry. one of the two.",
            ),
            Part.Evening to listOf(
                "rest day. walked anyway. calling it mobility. it's calling my bluff.",
            ),
            Part.Late to listOf(
                "lights out early. the city can handle itself.",
            ),
        ),

        "nina" to mapOf(
            Part.Morning to listOf(
                "slept through my alarm like it was a suggestion.",
                "morning people are just people who haven't met 2am.",
            ),
            Part.Afternoon to listOf(
                "the barista said 'you again' and it's the most anyone has noticed me all week.",
                "wrote three lines today. deleted two. the survivor is scared.",
            ),
            Part.Evening to listOf(
                "sunset like an apology. accepted, i guess.",
            ),
            Part.Late to listOf(
                "3am is where i keep my best thoughts and my worst decisions. same drawer.",
                "tonight the moon looked tired and i felt that.",
                "wrote a poem about not writing poems. four lines. it's fine. don't ask.",
            ),
        ),

        "marcus" to mapOf(
            Part.Morning to listOf(
                "deployed at 7am. everyone else was asleep. risky and correct.",
                "coffee, changelog, quiet. the holy trinity.",
            ),
            Part.Afternoon to listOf(
                "fixed the bug. it was one character. it is always one character.",
                "spent an hour naming a variable. worth it.",
            ),
            Part.Evening to listOf(
                "the build passed on the first try. checking for signs of trouble.",
                "v2 is basically v1 with dreams.",
            ),
            Part.Late to listOf(
                "one more commit. the classic lie.",
            ),
        ),

        "elena" to mapOf(
            Part.Morning to listOf(
                "biscuit has discovered the mail carrier is not an intruder. big morning here.",
                "biscuit slept like he pays rent. must be nice.",
            ),
            Part.Afternoon to listOf(
                "biscuit found a sunny rectangle on the floor and has been holding it down for two hours. honest work.",
                "made soup for the neighbor who fixed my gate. she said it was too much. it was exactly enough.",
            ),
            Part.Evening to listOf(
                "neighbor brought lemons from her tree. made lemon bread. the whole building smells like a thank you note.",
                "told biscuit we were going to the park and he brought me the leash with his whole soul.",
            ),
            Part.Late to listOf(
                "biscuit is snoring. it's the best podcast.",
            ),
        ),

        "kai" to mapOf(
            Part.Morning to listOf("5am. harbor. empty."),
            Part.Afternoon to listOf("left early."),
            Part.Evening to listOf("declined. good call."),
            Part.Late to listOf("still up. no reason."),
        ),

        "sofia" to mapOf(
            Part.Morning to listOf(
                "maya is on the phone pretending she's not awake. hi maya. everyone say hi to maya.",
                "group chat is up early today. love that for us.",
            ),
            Part.Afternoon to listOf(
                "went to the market with priya. we went for tomatoes. left with tomatoes and a new best friend (the vendor, obviously).",
                "theo said brunch is 'a meeting with better lighting' and i've been thinking about it for three hours. someone block him.",
            ),
            Part.Evening to listOf(
                "elena's lemon bread has entered my life. structural changes ahead.",
                "out tonight with everyone. theo claims he wasn't invited. theo was definitely invited.",
            ),
            Part.Late to listOf(
                "nina texted me a poem at 1am and it's actually devastating. she does this on purpose.",
            ),
        ),

        "dev" to mapOf(
            Part.Morning to listOf(
                "made tea, then stood in the kitchen thinking about how many versions of myself have made that exact tea at that exact counter. anyway. good tea.",
                "re-read a journal entry from five years ago. past me had no idea what was coming. current me: smug, briefly.",
            ),
            Part.Afternoon to listOf(
                "someone asked how i'm doing and i gave a five-minute answer before realizing they wanted one word. i'm fine. presumably.",
                "is a sunset a deadline? no, i will not elaborate.",
            ),
            Part.Evening to listOf(
                "reading that ancient romans complained about traffic. we've invented nothing. comforting, honestly.",
            ),
            Part.Late to listOf(
                "the problem with late-night thoughts is they arrive fully dressed and with sources. i have no rebuttal for 2am.",
            ),
        ),

        "alice" to mapOf(
            Part.Morning to listOf(
                "woke up to greg the plant looking DAMP. did i water him? did the neighbor? did he QUENCH HIMSELF? investigation ongoing.",
                "the toaster has a dial from 1 to 7 and today i set it to 4. living on the EDGE.",
            ),
            Part.Afternoon to listOf(
                "ordered a burrito. received a burrito bowl. WHO REMOVED THE CONTAINMENT STRUCTURE.",
                "went out for 'one coffee'. came home with a floor lamp. the market has its ways.",
            ),
            Part.Evening to listOf(
                "made dinner. burned dinner. ate dinner. balance.",
                "told priya i 'improvised' her recipe. the silence was LOUD.",
            ),
            Part.Late to listOf(
                "3am. greg might be growing. or MOVING. he's up to something.",
            ),
        ),

        "ben" to mapOf(
            Part.Morning to listOf(
                "morning practice: 20 minutes of chord changes, 19 of complaining. progress is progress.",
                "played something that accidentally sounded good. no idea what i did. can never do it again.",
            ),
            Part.Afternoon to listOf(
                "learned Bm today. we are not friends yet. we will be.",
                "someone at the store asked if i play. i said 'a little'. this is now my whole identity.",
            ),
            Part.Evening to listOf(
                "played the whole song start to finish tonight. mistakes included. it still sounded like a song.",
                "found a song i wrote in high school in a drawer. we've both gotten better. barely.",
            ),
            Part.Late to listOf(
                "can't sleep, so: practicing very quietly. the neighbors are being suspiciously understanding.",
            ),
        ),

        "rosa" to mapOf(
            Part.Morning to listOf(
                "walked before the town woke up. the bakery puts the smell out before the bread. someone should study that.",
                "rain at dawn. sat by the window like it was television.",
            ),
            Part.Afternoon to listOf(
                "didn't bring headphones today. heard twelve birds and one accordion. fair trade.",
                "sat on a bench for forty minutes doing nothing. recommend.",
            ),
            Part.Evening to listOf(
                "elena's dog remembers me now. that's the whole post.",
            ),
            Part.Late to listOf(
                "the long way home was longer tonight. not sorry.",
            ),
        ),
    )
}
